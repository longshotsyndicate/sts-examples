package uk.co.longshotsystems.sts;

import io.swagger.client.model.*;
import org.threeten.bp.OffsetDateTime;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * This small sample application connects to the websocket and stores all of the data it receives.
 * It also picks a random event and market and places a single bet on it, handling the pending situation
 * if required.
 */
public class SampleApp {
    public static void main(String[] args) {
        try {
            start(args[0], args[1], args[2]);
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    private static void start(String user, String pw, String apiRoot) throws Exception {
        // Cache of all the data we've received
        Map<Long, Update> dataStore = new HashMap<>();

        API api = new API(apiRoot);

        // Authorise before doing anything else
        AuthResponse resp = api.auth(user, pw);
        final UUID token = resp.getToken();

        // Open the websocket and cache the data
        api.odds(new API.SocketCallback() {
            boolean hasPlacedBet = false;

            @Override
            public void onMessage(WSMessage m) {
                storeData(dataStore, m);

                // TODO: publish data

                if (!hasPlacedBet) {
                    hasPlacedBet = true;
                    placeBet(api, dataStore);
                }
            }

            @Override
            public void onClosed() {
                System.out.println("websocket closed");
            }
        }, token);
    }

    private static void placeBet(API api, Map<Long, Update> dataStore) {
        for (var event : dataStore.values()) {
            if (event.getPrices().size() > 0) {
                placeBetOnEvent(api, event);
                break;
            }
        }
    }

    /**
     * Places a single bet on a random line for the given event.
     * @param api
     * @param event
     */
    private static void placeBetOnEvent(API api, Update event) {
        try {
            Odds odds = event.getPrices().get(0);

            BetAdviceRequest req = new BetAdviceRequest();

            String betId = UUID.randomUUID().toString();

            // The following are just random values for demo purposes:
            req.setTimestamp(OffsetDateTime.now());
            req.setEventId(event.getEventId());
            req.setBetType(odds.getBetType());
            req.setAccountHierarchy(Arrays.asList("senior_01", "agent_02"));
            req.setEndPunterId("username_123");
            req.setAccountId("senior_01agent_02");
            req.setCurrency(Currency.EUR);
            req.setPunterMaxStake(odds.getBack().getMaxStake());
            req.setPrice(odds.getBack().getPrice());
            req.setStake(odds.getBack().getMaxStake() / 2);
            req.setSide(BetAdviceRequest.SideEnum.BACK);
            req.setUniqueId(betId);
            req.setPositionTaking(0.5);

            System.out.printf("\nPlacing bet: %s", req);

            BetAdviceResponse resp = api.advice(req);

            System.out.printf("\nGot response: %s", resp);

            // Let the API know what we're doing
            confirm(api, betId, resp);

            if (resp.getStatus() == AdviceStatus.PENDING) {
                handlePending(api, betId, resp);
            }
        } catch (API.APIError e) {
            System.out.println(e.error);
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    /**
     * Pending is a special case. We need to poll the status endpoint until the pending period is complete.
     * @param api
     * @param betId
     * @param latestResp
     * @throws Exception
     */
    private static void handlePending(API api, String betId, BetAdviceResponse latestResp) throws Exception {
        while (latestResp.getStatus() == AdviceStatus.PENDING) {
            Thread.sleep(1000);

            latestResp = api.status(betId);

            System.out.printf("Got status: %s", latestResp.getStatus());
        }

        confirm(api, betId, latestResp);
    }

    /**
     * After receiving advice for a bet we need to confirm whether how we're proceeding (it is not mandatory
     * to follow the advice, however it is still required to confirm what action has taken in any case).
     * In this method we call confirm with the same advice status as we received.
     * i.e. we're following the advice we were given.
     * @param api
     * @param betId
     * @param resp
     * @throws Exception
     */
    private static void confirm(API api, String betId, BetAdviceResponse resp) throws Exception {
        ClientTradingDecision confirmation = new ClientTradingDecision();
        confirmation.setStatus(resp.getStatus());
        confirmation.setCurrency(Currency.EUR);

        // don't need to set a stake or price if we're rejecting.
        if (resp.getStatus() != AdviceStatus.REJECTED) {
            confirmation.setPrice(resp.getPrice());
            confirmation.setStake(resp.getStake());
        }

        api.confirm(betId, confirmation);
    }

    private static void storeData(Map<Long, Update> dataStore, WSMessage msg) {
        for (Update update : msg.getUpdates()) {
            Update existing = dataStore.get(update.getEventId());

            if (existing == null) {
                dataStore.put(update.getEventId(), update);
            } else {
                // If we already have an entry for this event we simply need to update it

                // we always take the latest prices
                existing.setPrices(update.getPrices());

                // EventDescription is optional so take it if available
                EventDescription desc = update.getEventDescription();
                if (desc != null) {
                    existing.setEventDescription(desc);
                }

                // LiveData is optional so take it if available
                LiveData live = update.getLive();
                if (live != null) {
                    existing.setLive(live);
                }
            }
        }
    }
}
