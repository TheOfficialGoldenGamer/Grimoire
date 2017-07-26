package net.bemacized.grimoire.pricing;

import io.magicthegathering.javasdk.resource.Card;
import net.bemacized.grimoire.pricing.apis.MagicCardMarketAPI;
import net.bemacized.grimoire.pricing.apis.StoreAPI;
import net.bemacized.grimoire.pricing.apis.TCGPlayerAPI;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class PricingManager {

	private final static Logger LOG = Logger.getLogger(PricingManager.class.getName());

	private SetDictionary setDictionary;

	private List<StoreAPI> stores;

	public PricingManager() {
		this.setDictionary = new SetDictionary();
		this.stores = new ArrayList<StoreAPI>() {{
			add(new MagicCardMarketAPI(
					System.getenv("MCM_HOST"),
					System.getenv("MCM_TOKEN"),
					System.getenv("MCM_SECRET")
			));
			add(new TCGPlayerAPI(
					System.getenv("TCG_HOST"),
					System.getenv("TCG_KEY")
			));
		}};
	}

	public List<StoreCardPrice> getPricing(Card card) {
		return stores.stream().map(store -> {
			try {
				StoreAPI.StoreCardPriceRecord price = store.getPrice(card);
				return new StoreCardPrice((price == null) ? StoreCardPriceStatus.CARD_UNKNOWN : StoreCardPriceStatus.SUCCESS, card, store.getStoreName(), store.getStoreId(), price);
			} catch (StoreAPI.StoreServerErrorException e) {
				return new StoreCardPrice(StoreCardPriceStatus.SERVER_ERROR, card, store.getStoreName(), store.getStoreId(), null);
			} catch (StoreAPI.StoreAuthException e) {
				return new StoreCardPrice(StoreCardPriceStatus.AUTH_ERROR, card, store.getStoreName(), store.getStoreId(), null);
			} catch (StoreAPI.UnknownStoreException e) {
				return new StoreCardPrice(StoreCardPriceStatus.UNKNOWN_ERROR, card, store.getStoreName(), store.getStoreId(), null);
			} catch (StoreAPI.StoreSetUnknownException e) {
				return new StoreCardPrice(StoreCardPriceStatus.SET_UNKNOWN, card, store.getStoreName(), store.getStoreId(), null);
			}
		}).collect(Collectors.toList());
	}

	public void init() {
		this.stores.forEach(store -> {
			try {
				store.updateSetDictionary(setDictionary);
			} catch (StoreAPI.StoreAuthException e) {
				LOG.log(Level.SEVERE, "Authentication error occurred with " + store.getStoreName() + " while updating the set dictionary", e);
			} catch (StoreAPI.StoreServerErrorException e) {
				LOG.log(Level.WARNING, "Server error occurred with " + store.getStoreName() + " while updating the set dictionary", e);
			} catch (StoreAPI.UnknownStoreException e) {
				LOG.log(Level.SEVERE, "Unknown error occurred with " + store.getStoreName() + " while updating the set dictionary", e);
			}
		});
	}

	public SetDictionary getSetDictionary() {
		return setDictionary;
	}

	public class StoreCardPrice {
		private StoreCardPriceStatus status;
		private Card card;
		private String storeName;
		private String storeId;
		private StoreAPI.StoreCardPriceRecord record;

		public StoreCardPrice(StoreCardPriceStatus status, Card card, String storeName, String storeId, StoreAPI.StoreCardPriceRecord record) {
			this.status = status;
			this.card = card;
			this.storeName = storeName;
			this.storeId = storeId;
			this.record = record;
		}

		public StoreCardPriceStatus getStatus() {
			return status;
		}

		public Card getCard() {
			return card;
		}

		public String getStoreName() {
			return storeName;
		}

		public String getStoreId() {
			return storeId;
		}

		public StoreAPI.StoreCardPriceRecord getRecord() {
			return record;
		}
	}

	public enum StoreCardPriceStatus {
		SUCCESS,
		SERVER_ERROR,
		AUTH_ERROR,
		SET_UNKNOWN,
		UNKNOWN_ERROR,
		CARD_UNKNOWN
	}
}