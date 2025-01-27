package langcontrol.app.spaced_repetition;

import langcontrol.app.deck.LanguageCode;
import langcontrol.app.flashcard.Flashcard;
import langcontrol.app.flashcard.FlashcardService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SpacedRepetitionController.class)
class SpacedRepetitionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FlashcardService mockedFlashcardService;

    @MockBean
    private SpacedRepetitionService mockedSpacedRepetitionService;

    public static Stream<Arguments> invalidHandleFlashcardRatingMethodParams() {
        return Stream.of(
                Arguments.arguments(new FlashcardRatingDTO(null, 2L), new ArrayDeque<>()),
                Arguments.arguments(new FlashcardRatingDTO(RatingType.LEARN_NEXT, null), new ArrayDeque<>()),
                Arguments.arguments(new FlashcardRatingDTO(RatingType.LEARN_NEXT, 0L), new ArrayDeque<>())
        );
    }

    public static ArrayDeque<Flashcard> threeElementFlashcardArrayDequeFirstInLearnMode() {
        Flashcard card1 = Flashcard.inInitialLearnModeState()
                .front("front 1")
                .back("back 1")
                .sourceLanguage(LanguageCode.ENGLISH)
                .targetLanguage(LanguageCode.GERMAN)
                .deck(null)
                .build();
        Flashcard card2 = Flashcard.inInitialReviewModeState()
                .front("front 2")
                .back("back 2")
                .sourceLanguage(LanguageCode.ENGLISH)
                .targetLanguage(LanguageCode.SPANISH)
                .deck(null)
                .build();
        Flashcard card3 = Flashcard.inInitialReviewModeState()
                .front("front 3")
                .back("back 3")
                .sourceLanguage(LanguageCode.SPANISH)
                .targetLanguage(LanguageCode.ENGLISH)
                .deck(null)
                .build();
        return new ArrayDeque<>(List.of(card1, card2, card3));
    }

    public static ArrayDeque<Flashcard> threeElementFlashcardArrayDequeFirstInReviewMode() {
        Flashcard card1 = Flashcard.inInitialReviewModeState()
                .front("front 1")
                .back("back 1")
                .sourceLanguage(LanguageCode.ENGLISH)
                .targetLanguage(LanguageCode.SPANISH)
                .deck(null)
                .build();
        Flashcard card2 = Flashcard.inInitialLearnModeState()
                .front("front 2")
                .back("back 2")
                .sourceLanguage(LanguageCode.ENGLISH)
                .targetLanguage(LanguageCode.GERMAN)
                .deck(null)
                .build();
        Flashcard card3 = Flashcard.inInitialReviewModeState()
                .front("front 3")
                .back("back 3")
                .sourceLanguage(LanguageCode.SPANISH)
                .targetLanguage(LanguageCode.ENGLISH)
                .deck(null)
                .build();
        return new ArrayDeque<>(List.of(card1, card2, card3));
    }

    public static ArrayDeque<Flashcard> oneElementFlashcardArrayDeque() {
        Flashcard card3 = Flashcard.inInitialReviewModeState()
                .front("front 1")
                .back("back 1")
                .sourceLanguage(LanguageCode.SPANISH)
                .targetLanguage(LanguageCode.ENGLISH)
                .deck(null)
                .build();
        return new ArrayDeque<>(List.of(card3));
    }


    @WithAnonymousUser
    @Test
    void openDeck_ShouldReturnStatusCodeUnauthorized_WhenUserIsAnonymous() throws Exception {
        mockMvc.perform(get("/review")
                        .param("deckId", "1")
                        .param("timezone", "Europe/Warsaw"))
                .andExpect(status().isUnauthorized());
    }


    @WithMockUser(username = "user@email.com")
    @Test
    void openDeck_ShouldRedirectToDecksPage_WhenReadyForReviewResultIsEmpty() throws Exception {
        // given
        given(mockedFlashcardService.fetchReadyForReviewShuffledWithLimit(
                Mockito.anyLong(), Mockito.anyString(), Mockito.anyInt()))
                .willReturn(new ArrayDeque<>());

        // when
        MvcResult mvcResult = mockMvc.perform(get("/review")
                        .param("deckId", "1")
                        .param("timezone", "Europe/Warsaw"))
                .andExpect(status().isFound())
                .andReturn();

        // then
        then(mockedFlashcardService).should(times(1))
                .fetchReadyForReviewShuffledWithLimit(
                        Mockito.anyLong(), Mockito.anyString(), Mockito.anyInt());
        assertEquals("redirect:/decks", Objects.requireNonNull(mvcResult.getModelAndView()).getViewName());
    }


    @WithMockUser(username = "user@email.com")
    @ParameterizedTest
    @CsvSource(value = {
            "0,Europe/Warsaw",
            "-1,Europe/Warsaw",
            "1, ",
            "1,'  '"},
            ignoreLeadingAndTrailingWhitespace = false)
    void openDeck_ShouldReturnBadRequestStatusCode_WhenParametersAreNotValid(long deckIdParam,
                                                                         String timezoneIdParam) throws Exception {
        mockMvc.perform(get("/review")
                        .param("deckId", String.valueOf(deckIdParam))
                        .param("timezone", timezoneIdParam))
                .andExpect(status().isBadRequest());
    }

    @WithMockUser(username = "user@email.com")
    @Test
    void openDeck_ShouldDisplayLearnPage_WhenTheFirstReadyForReviewCardIsInLearnMode() throws Exception {
        // given
        long deckId = 23L;
        String timezoneId = "America/Los_Angeles";
        int limit = 10;
        Flashcard cardLearnMode = Flashcard.inInitialLearnModeState()
                .front("learn front 1")
                .back("learn back 1")
                .sourceLanguage(LanguageCode.ENGLISH)
                .targetLanguage(LanguageCode.SPANISH).build();
        cardLearnMode.setId(7L);
        Flashcard cardReviewMode1 = Flashcard.inInitialReviewModeState()
                .front("review front 1")
                .back("review back 1")
                .sourceLanguage(LanguageCode.SPANISH)
                .targetLanguage(LanguageCode.GERMAN).build();
        cardReviewMode1.setId(36L);
        Flashcard cardReviewMode2 = Flashcard.inInitialReviewModeState()
                .front("review front 2")
                .back("review back 2")
                .sourceLanguage(LanguageCode.SPANISH)
                .targetLanguage(LanguageCode.ENGLISH).build();
        LocalDateTime lastReview = LocalDateTime
                .of(2023, 3, 14, 16, 37, 21);
        cardReviewMode2.setLastReviewInUTC(lastReview);
        cardReviewMode2.setCurrentIntervalDays(11.6);
        cardReviewMode2.setNextReviewInUTC(lastReview.plusDays(12));
        cardReviewMode2.setNextReviewWithoutTimeInUTC(lastReview.plusDays(12).toLocalDate());
        cardReviewMode2.setId(76L);
        Deque<Flashcard> learnCardFirstDeque = new ArrayDeque<>(List.of(cardLearnMode, cardReviewMode1, cardReviewMode2));

        given(mockedFlashcardService.fetchReadyForReviewShuffledWithLimit(deckId, timezoneId, limit))
                .willReturn(learnCardFirstDeque);

        // when
        mockMvc.perform(get("/review")
                        .param("deckId", String.valueOf(deckId))
                        .param("timezone", timezoneId))
                .andExpect(status().isOk())
                .andExpect(view().name("deck-learn-page"))
                .andExpect(model().attribute("currentCard",
                        hasProperty("id", equalTo(7L))))
                .andExpect(model().attribute("reviewCards", hasSize(3)))
                .andExpect(model().attribute("reviewCards", allOf(
                        hasItem(equalTo(cardLearnMode)),
                        hasItem(equalTo(cardReviewMode1)),
                        hasItem(equalTo(cardReviewMode2))))
                );

        // then
        then(mockedFlashcardService)
                .should(times(1))
                .fetchReadyForReviewShuffledWithLimit(deckId, timezoneId, limit);
    }

    @WithMockUser(username = "user@email.com")
    @Test
    void openDeck_ShouldDisplayReviewPage_WhenTheFirstReadyForReviewCardIsInReviewMode() throws Exception {
        // given
        long deckId = 23L;
        String timezoneId = "America/Los_Angeles";
        int limit = 10;
        Flashcard cardLearnMode = Flashcard.inInitialLearnModeState()
                .front("learn front 1")
                .back("learn back 1")
                .sourceLanguage(LanguageCode.ENGLISH)
                .targetLanguage(LanguageCode.SPANISH).build();
        cardLearnMode.setId(7L);
        Flashcard cardReviewMode1 = Flashcard.inInitialReviewModeState()
                .front("review front 1")
                .back("review back 1")
                .sourceLanguage(LanguageCode.SPANISH)
                .targetLanguage(LanguageCode.GERMAN).build();
        cardReviewMode1.setId(36L);
        Flashcard cardReviewMode2 = Flashcard.inInitialReviewModeState()
                .front("review front 2")
                .back("review back 2")
                .sourceLanguage(LanguageCode.SPANISH)
                .targetLanguage(LanguageCode.ENGLISH).build();
        LocalDateTime lastReview = LocalDateTime
                .of(2023, 3, 14, 16, 37, 21);
        cardReviewMode2.setLastReviewInUTC(lastReview);
        cardReviewMode2.setCurrentIntervalDays(11.6);
        cardReviewMode2.setNextReviewInUTC(lastReview.plusDays(12));
        cardReviewMode2.setNextReviewWithoutTimeInUTC(lastReview.plusDays(12).toLocalDate());
        cardReviewMode2.setId(86L);
        Deque<Flashcard> reviewCardFirstDeque = new ArrayDeque<>(List.of(cardReviewMode2, cardLearnMode, cardReviewMode1));

        given(mockedFlashcardService.fetchReadyForReviewShuffledWithLimit(deckId, timezoneId, limit))
                .willReturn(reviewCardFirstDeque);

        // when
        mockMvc.perform(get("/review")
                        .param("deckId", String.valueOf(deckId))
                        .param("timezone", timezoneId))
                .andExpect(status().isOk())
                .andExpect(view().name("deck-review-page"))
                .andExpect(model().attribute("currentCard",
                        hasProperty("id", equalTo(86L))
                ))
                .andExpect(model().attribute("reviewCards", hasSize(3)))
                .andExpect(model().attribute("reviewCards", allOf(
                        hasItem(equalTo(cardLearnMode)),
                        hasItem(equalTo(cardReviewMode1)),
                        hasItem(equalTo(cardReviewMode2)))
                ));

        // then
        then(mockedFlashcardService)
                .should(times(1))
                .fetchReadyForReviewShuffledWithLimit(deckId, timezoneId, limit);
    }

    @WithMockUser(username = "user@email.com")
    @ParameterizedTest
    @MethodSource("invalidHandleFlashcardRatingMethodParams")
    void handleFlashcardRating_ShouldReturnBadRequestStatusCode_WhenParametersAreInvalid(
            FlashcardRatingDTO invalidRatingDto, Deque<Flashcard> invalidCardDeque) throws Exception {
        mockMvc.perform(post("/review")
                        .with(csrf())
                        .flashAttr("rating", invalidRatingDto)
                        .flashAttr("reviewCards", invalidCardDeque))
                .andExpect(status().isBadRequest());
    }

    @WithMockUser(username = "user@email.com")
    @ParameterizedTest
    @MethodSource("invalidHandleFlashcardRatingMethodParams")
    void handleFlashcardRating_ShouldReturnBadRequestStatusCode_WhenParameterIsNull(
            FlashcardRatingDTO invalidRatingDto, Deque<Flashcard> invalidCardDeque) throws Exception {
        FlashcardRatingDTO ratingDTO = new FlashcardRatingDTO(RatingType.LEARN_NEXT, 2L);

        mockMvc.perform(post("/review")
                        .with(csrf())
                        .flashAttr("rating", ratingDTO))
                .andExpect(status().isBadRequest());
    }

    @WithMockUser(username = "user@email.com")
    @Test
    void handleFlashcardRating_ShouldHandleRatingAndRemoveTheTopCardFromDeque_WhenParametersAreValid() throws Exception {
        // given
        FlashcardRatingDTO ratingDto = new FlashcardRatingDTO(RatingType.LEARN_NEXT, 2L);
        Deque<Flashcard> readyForReviewCardsDeque = threeElementFlashcardArrayDequeFirstInLearnMode();
        Deque<Flashcard> expectedCardsForReviewDeque = new ArrayDeque<>(readyForReviewCardsDeque);
        expectedCardsForReviewDeque.poll();

        // when
        mockMvc.perform(post("/review")
                        .with(csrf())
                        .flashAttr("rating", ratingDto)
                        .flashAttr("reviewCards", readyForReviewCardsDeque))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/review/next"))
                .andExpect(flash().attribute("reviewCards", hasSize(expectedCardsForReviewDeque.size())));

        // then
        then(mockedSpacedRepetitionService).should(times(1))
                .applyRating(ratingDto.getFlashcardId(), ratingDto.getRatingType());
        assertEquals(expectedCardsForReviewDeque.element().getFront(), readyForReviewCardsDeque.element().getFront());
        assertEquals(expectedCardsForReviewDeque.size(), readyForReviewCardsDeque.size());
    }

    @WithMockUser(username = "user@email.com")
    @Test
    void handleFlashcardRating_ShouldRedirectToMainDecksPage_WhenTheLastCardIsRated() throws Exception {
        // given
        FlashcardRatingDTO ratingDto = new FlashcardRatingDTO(RatingType.LEARN_NEXT, 2L);
        Deque<Flashcard> readyForReviewCardsOneElemDeque = oneElementFlashcardArrayDeque();

        // when
        mockMvc.perform(post("/review")
                        .with(csrf())
                        .flashAttr("rating", ratingDto)
                        .flashAttr("reviewCards", readyForReviewCardsOneElemDeque))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/decks"));

        // then
        then(mockedSpacedRepetitionService).should(times(1))
                .applyRating(ratingDto.getFlashcardId(), ratingDto.getRatingType());
        assertTrue(readyForReviewCardsOneElemDeque.isEmpty());
    }

    @WithMockUser(username = "user@email.com")
    @Test
    void reviewNextCard_ShouldDisplayLearnPage_WhenTheTopReadyForReviewCardIsInLearnMode() throws Exception {
        // given
        Deque<Flashcard> readyForReviewCardsDeque = threeElementFlashcardArrayDequeFirstInLearnMode();

        // when
        mockMvc.perform(get("/review/next")
                        .flashAttr("reviewCards", readyForReviewCardsDeque))
        // then
                .andExpect(view().name("deck-learn-page"))
                .andExpect(model().attribute("reviewCards", readyForReviewCardsDeque))
                .andExpect(model().attribute("currentCard", readyForReviewCardsDeque.element()))
                .andExpect(model().attribute("rating", instanceOf(FlashcardRatingDTO.class)));
    }

    @WithMockUser(username = "user@email.com")
    @Test
    void reviewNextCard_ShouldDisplayReviewPage_WhenTheTopReadyForReviewCardIsInReviewMode() throws Exception {
        // given
        Deque<Flashcard> readyForReviewCardsDeque = threeElementFlashcardArrayDequeFirstInReviewMode();

        // when
        mockMvc.perform(get("/review/next")
                        .flashAttr("reviewCards", readyForReviewCardsDeque))
        // then
                .andExpect(view().name("deck-review-page"))
                .andExpect(model().attribute("reviewCards", readyForReviewCardsDeque))
                .andExpect(model().attribute("currentCard", readyForReviewCardsDeque.element()))
                .andExpect(model().attribute("rating", instanceOf(FlashcardRatingDTO.class)));
    }

    @WithMockUser(username = "user@email.com")
    @Test
    void reviewNextCard_ShouldRedirectToMainDecksPage_WhenReadyForReviewCardsDequeIsEmpty() throws Exception {
        // given
        Deque<Flashcard> readyForReviewCardsDeque = new ArrayDeque<>();

        // when
        mockMvc.perform(get("/review/next")
                        .flashAttr("reviewCards", readyForReviewCardsDeque))
                .andExpect(redirectedUrl("/decks"));
    }
}