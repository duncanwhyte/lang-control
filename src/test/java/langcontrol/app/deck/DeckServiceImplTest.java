package langcontrol.app.deck;

import langcontrol.app.exception.AccessNotAllowedException;
import langcontrol.app.exception.GeneralNotFoundException;
import langcontrol.app.exception.DeckCreationException;
import langcontrol.app.account.Account;
import langcontrol.app.security.DefinedRoleValue;
import langcontrol.app.security.Role;
import langcontrol.app.user_profile.UserProfile;
import langcontrol.app.user_profile.UserProfileRepository;
import langcontrol.app.util.PrincipalRetriever;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class DeckServiceImplTest {

    private DeckServiceImpl underTest;

    @Mock
    private DeckRepository mockedDeckRepository;

    @Mock
    private UserProfileRepository mockedUserProfileRepository;

    @Captor
    private ArgumentCaptor<Deck> deckCaptor;


    @BeforeEach
    void setUp() {
        this.underTest = new DeckServiceImpl(mockedDeckRepository, mockedUserProfileRepository, userProfileService, flashcardService);
    }

    @Test
    void createNewDeck_ShouldThrowException_WhenTargetAndSourceLanguageAreTheSame() {
        // given
        LanguageCode langCode = LanguageCode.ENGLISH;
        Deck deckSameLanguages = new Deck(null, "Invalid Deck",
                null, langCode, langCode, null);
        RuntimeException expectedException = null;

        // when
        try {
            underTest.createNewDeck(deckSameLanguages);
        } catch (RuntimeException e) {
            expectedException = e;
        }

        // then
        assertTrue(expectedException instanceof DeckCreationException);
    }

    @Test
    void createNewDeck_ShouldAddNewDeckToUserProfile_WhenDataIsCorrect() {
        // given
        Deck testDeck = new Deck(7L, "Invalid Deck",
                null, LanguageCode.ENGLISH, LanguageCode.GERMAN, null);
        Account testAccount = new Account(
                7L, "test@example.com",
                "aBt3%4gh45srSuiAe5h%EA5h",
                List.of(new Role(1L, DefinedRoleValue.USER)),
                true, true,
                true, true);
        UserProfile testUserProfile = new UserProfile(7L, "John Doe", testAccount, new ArrayList<>());
        testAccount.setUserProfile(testUserProfile);
        given(mockedUserProfileRepository.findByAccount(testAccount)).willReturn(Optional.of(testUserProfile));
        try (MockedStatic<PrincipalRetriever> mockedStaticPR = Mockito.mockStatic(PrincipalRetriever.class)) {
            mockedStaticPR.when(PrincipalRetriever::retrieveAccount).thenReturn(testAccount);

        // when
            underTest.createNewDeck(testDeck);
        }

        // then
        assertTrue(testUserProfile.getDecks().contains(testDeck));
    }

    @Test
    void getAllDecks_ShouldReturnAllDecksOfTheCurrentUser() {
        // given
        Deck deck1 = new Deck(12L, "Deck One", null,
                LanguageCode.SPANISH, LanguageCode.ENGLISH, new ArrayList<>());
        Deck deck2 = new Deck(15L, "Deck Two", null,
                LanguageCode.SPANISH, LanguageCode.GERMAN, new ArrayList<>());
        List<Deck> deckList = new ArrayList<>(List.of(deck1, deck2));
        List<DeckView> deckViewList = deckList
                .stream()
                .map(d -> new DeckView(d.getId(), d.getName(),
                        d.getTargetLanguage(), d.getSourceLanguage()))
                .toList();

        Account testAccount = new Account(
                7L, "test@example.com",
                "aBt3%4gh45srSuiAe5h%EA5h",
                List.of(new Role(1L, DefinedRoleValue.USER)),
                true, true,
                true, true);
        UserProfile testUserProfile = new UserProfile(7L, "John Doe", testAccount, deckList);
        testAccount.setUserProfile(testUserProfile);
        deck1.setUserProfile(testUserProfile);
        deck2.setUserProfile(testUserProfile);
        given(mockedDeckRepository.findByUserProfile(testUserProfile)).willReturn(deckViewList);
        try (MockedStatic<PrincipalRetriever> mockedStaticPR = Mockito.mockStatic(PrincipalRetriever.class)) {
            mockedStaticPR.when(PrincipalRetriever::retrieveAccount).thenReturn(testAccount);

        // when
            List<DeckView> result = underTest.getAllDecks();

        // then
            assertEquals(deckViewList, result);
        }
    }

    @Test
    void getDeckById_ShouldThrowException_WhenDeckIsNotFound() {
        //given
        long testDeckId = 34L;
        given(mockedDeckRepository.findById(Mockito.anyLong())).willReturn(Optional.empty());
        try (MockedStatic<PrincipalRetriever> mockedStaticPR = Mockito.mockStatic(PrincipalRetriever.class)) {

        // when + then
           assertThrows(GeneralNotFoundException.class, () -> underTest.getDeckById(testDeckId));
        }
    }
    @Test
    void getDeckById_ShouldThrowException_WhenFoundDeckHasDifferentUserProfileThanTheCurrentOne() {
        // given
        long testDeckId = 34L;
        Account testAccount = new Account(
                7L, "test@example.com",
                "aBt3%4gh45srSuiAe5h%EA5h",
                List.of(new Role(1L, DefinedRoleValue.USER)),
                true, true,
                true, true);
        UserProfile testUserProfile = new UserProfile(7L, "John Doe", testAccount, new ArrayList<>());
        testAccount.setUserProfile(testUserProfile);
        UserProfile testUserProfile2 = new UserProfile(14L, "Jane Smith", null, new ArrayList<>());
        Deck testDeck = new Deck(testDeckId, "Deck One", testUserProfile2,
                LanguageCode.SPANISH, LanguageCode.ENGLISH, new ArrayList<>());

        given(mockedDeckRepository.findById(Mockito.anyLong())).willReturn(Optional.of(testDeck));
        try (MockedStatic<PrincipalRetriever> mockedStaticPR = Mockito.mockStatic(PrincipalRetriever.class)) {
            mockedStaticPR.when(PrincipalRetriever::retrieveAccount).thenReturn(testAccount);

        // when + then
            assertThrows(AccessNotAllowedException.class, () -> underTest.getDeckById(testDeckId));
        }
    }

    @Test
    void getDeckById_ShouldReturnFoundDeck() {
        // given
        long testDeckId = 34L;
        Account testAccount = new Account(
                7L, "test@example.com",
                "aBt3%4gh45srSuiAe5h%EA5h",
                List.of(new Role(1L, DefinedRoleValue.USER)),
                true, true,
                true, true);
        UserProfile testUserProfile = new UserProfile(7L, "John Doe", testAccount, new ArrayList<>());
        testAccount.setUserProfile(testUserProfile);
        Deck testDeck = new Deck(testDeckId, "Deck One", testUserProfile,
                LanguageCode.SPANISH, LanguageCode.ENGLISH, new ArrayList<>());

        given(mockedDeckRepository.findById(Mockito.anyLong())).willReturn(Optional.of(testDeck));
        try (MockedStatic<PrincipalRetriever> mockedStaticPR = Mockito.mockStatic(PrincipalRetriever.class)) {
            mockedStaticPR.when(PrincipalRetriever::retrieveAccount).thenReturn(testAccount);

        // when
            Deck result = underTest.getDeckById(testDeckId);

        // then
            assertEquals(testDeck.getId(), result.getId());
            assertEquals(testDeck.getName(), result.getName());
            assertEquals(testDeck.getSourceLanguage(), result.getSourceLanguage());
            assertEquals(testDeck.getTargetLanguage(), result.getTargetLanguage());
        }
    }

    @Test
    void deleteDeck_ShouldDeleteDeckWithGivenId() {
        // given
        Mockito.doNothing().when(mockedDeckRepository).deleteById(Mockito.anyLong());
        long testDeckId = 34L;

        // when
        underTest.deleteDeck(testDeckId);
        
        // then
        then(mockedDeckRepository).should().deleteById(testDeckId);
    }
}