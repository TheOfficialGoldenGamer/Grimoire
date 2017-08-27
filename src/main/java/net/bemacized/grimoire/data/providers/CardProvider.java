package net.bemacized.grimoire.data.providers;

import net.bemacized.grimoire.data.models.card.MtgCard;
import net.bemacized.grimoire.data.models.mtgjson.MtgJsonCard;
import net.bemacized.grimoire.data.models.scryfall.ScryfallCard;
import net.bemacized.grimoire.data.models.scryfall.ScryfallSet;
import net.bemacized.grimoire.data.retrievers.CardImageRetriever;
import net.bemacized.grimoire.data.retrievers.ScryfallRetriever;
import net.bemacized.grimoire.utils.TimedValue;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class CardProvider {

	private final Logger LOG;
	private CardImageRetriever imageRetriever;
	private MtgJsonProvider mtgJsonProvider;

	private List<ScryfallSet> sets;
	private TimedValue<List<MtgCard>> cachedTokens;

	public CardProvider() {
		sets = new ArrayList<>();
		LOG = Logger.getLogger(this.getClass().getName());
		imageRetriever = new CardImageRetriever();
		mtgJsonProvider = new MtgJsonProvider();
		mtgJsonProvider.load();
		cachedTokens = new TimedValue<List<MtgCard>>(24 * 60 * 60 * 1000) {
			@Nullable
			@Override
			public List<MtgCard> refresh() {
				try {
					return getCardsByScryfallQuery("++t:token");
				} catch (ScryfallRetriever.ScryfallRequest.UnknownResponseException e) {
					LOG.log(Level.SEVERE, "An unknown error occurred with Scryfall", e);
				} catch (ScryfallRetriever.ScryfallRequest.ScryfallErrorException e) {
					LOG.log(Level.SEVERE, "An error occurred with Scryfall", e);
				} catch (ScryfallRetriever.ScryfallRequest.NoResultException e) {
					LOG.log(Level.SEVERE, "Scryfall did not return any tokens", e);
				}
				return null;
			}
		};
		LOG.info("Loading tokens...");
		int tokenCount = cachedTokens.get().size();
		LOG.info(tokenCount + " tokens loaded!");
	}

	public CardImageRetriever getImageRetriever() {
		return imageRetriever;
	}

	@Nonnull
	public MtgCard getCardByScryfallId(@Nonnull String scryfallId) throws ScryfallRetriever.ScryfallRequest.UnknownResponseException, ScryfallRetriever.ScryfallRequest.NoResultException, ScryfallRetriever.ScryfallRequest.ScryfallErrorException {
		//TODO: Get from cache list
		MtgCard card = new MtgCard(scryfallId);
		return card;
	}

	@Nonnull
	public MtgCard getCardByMultiverseId(int multiverseId) throws ScryfallRetriever.ScryfallRequest.UnknownResponseException, ScryfallRetriever.ScryfallRequest.NoResultException, ScryfallRetriever.ScryfallRequest.ScryfallErrorException {
		//TODO: Get from cache list
		MtgCard card = new MtgCard(ScryfallRetriever.getCardByMultiverseId(multiverseId));
		return card;
	}

	public MtgJsonProvider getMtgJsonProvider() {
		return mtgJsonProvider;
	}

	public List<MtgCard> getCardsByScryfallQuery(@Nonnull String query) throws ScryfallRetriever.ScryfallRequest.UnknownResponseException, ScryfallRetriever.ScryfallRequest.NoResultException, ScryfallRetriever.ScryfallRequest.ScryfallErrorException {
		return getCardsByScryfallQuery(query, -1);
	}

	public List<MtgCard> getCardsByScryfallQuery(@Nonnull String query, int maxResults) throws ScryfallRetriever.ScryfallRequest.UnknownResponseException, ScryfallRetriever.ScryfallRequest.NoResultException, ScryfallRetriever.ScryfallRequest.ScryfallErrorException {
		return ScryfallRetriever.getCardsFromQuery(query, maxResults).parallelStream().map(MtgCard::new).collect(Collectors.toList());
	}

	@Nullable
	public ScryfallSet getSetByNameOrCode(@Nonnull String nameOrCode) {
		// Attempt retrieval from cache
		ScryfallSet set = sets.parallelStream().filter(s -> s.getCode().equalsIgnoreCase(nameOrCode)).findFirst().orElse(sets.parallelStream().filter(s -> s.getName().toLowerCase().contains(nameOrCode.toLowerCase())).findFirst().orElse(null));
		if (set != null) return set;
		// Retrieve as code from Scryfall
		try {
			set = ScryfallRetriever.getSet(nameOrCode);
			this.sets.add(set);
			return set;
		} catch (ScryfallRetriever.ScryfallRequest.UnknownResponseException e) {
			LOG.log(Level.SEVERE, "An unknown error occurred with Scryfall", e);
			return null;
		} catch (ScryfallRetriever.ScryfallRequest.ScryfallErrorException e) {
			LOG.log(Level.SEVERE, "An error occurred with Scryfall", e);
			return null;
		} catch (ScryfallRetriever.ScryfallRequest.NoResultException ignored) {
		}
		// Must be a name, try name matching
		try {
			List<ScryfallSet> sets = ScryfallRetriever.getSets();
			sets.parallelStream().filter(s -> sets.parallelStream().noneMatch(_s -> _s.getCode().equalsIgnoreCase(s.getCode()))).forEach(this.sets::add);
			set = sets.parallelStream().filter(s -> s.getName().equalsIgnoreCase(nameOrCode) || s.getCode().equalsIgnoreCase(nameOrCode)).findFirst().orElse(null);
			return set;
		} catch (ScryfallRetriever.ScryfallRequest.UnknownResponseException e) {
			LOG.log(Level.SEVERE, "An unknown error occurred with Scryfall", e);
			return null;
		} catch (ScryfallRetriever.ScryfallRequest.ScryfallErrorException e) {
			LOG.log(Level.SEVERE, "An error occurred with Scryfall", e);
			return null;
		}
	}

	@Nullable
	public MtgCard matchNonEnglishCardName(@Nonnull String query, @Nullable ScryfallSet set) {
		MtgJsonCard mjc = mtgJsonProvider.getCards().parallelStream()
				.filter(c -> set == null || (c.getSetCode().equalsIgnoreCase(set.getCode()) || c.getSetName().equalsIgnoreCase(set.getName())))
				.filter(c -> !c.getLanguage().equalsIgnoreCase("English"))
				.filter(c -> c.getName().equalsIgnoreCase(query))
				.findFirst()
				.orElse(null);
		if (mjc == null) return null;
		MtgJsonCard engVersion = mjc.getAllLanguages().stream().filter(c -> c.getLanguage().equalsIgnoreCase("English")).findFirst().orElse(null);
		if (engVersion == null) return null;
		MtgCard card;
		try {
			card = getCardByMultiverseId(engVersion.getMultiverseid());
			return new MtgCard(card.getScryfallCard(), mjc);
		} catch (ScryfallRetriever.ScryfallRequest.UnknownResponseException e) {
			LOG.log(Level.SEVERE, "An unknown error occurred with Scryfall", e);
			return null;
		} catch (ScryfallRetriever.ScryfallRequest.NoResultException e) {
			return null;
		} catch (ScryfallRetriever.ScryfallRequest.ScryfallErrorException e) {
			LOG.log(Level.SEVERE, "An error occurred with Scryfall", e);
			return null;
		}
	}

	public List<MtgCard> getCachedTokens() {
		return cachedTokens.get();
	}

	@Nullable
	public MtgCard getRandomCardByScryfallQuery(String query) throws ScryfallRetriever.ScryfallRequest.ScryfallErrorException, ScryfallRetriever.ScryfallRequest.UnknownResponseException, ScryfallRetriever.ScryfallRequest.NoResultException {
		ScryfallCard card = ScryfallRetriever.getRandomCardFromQuery(query);
		if (card == null) return null;
		return new MtgCard(card);
	}
}
