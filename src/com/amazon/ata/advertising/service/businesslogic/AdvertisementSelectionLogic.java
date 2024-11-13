package com.amazon.ata.advertising.service.businesslogic;

import com.amazon.ata.advertising.service.dao.ReadableDao;
import com.amazon.ata.advertising.service.model.AdvertisementContent;
import com.amazon.ata.advertising.service.model.EmptyGeneratedAdvertisement;
import com.amazon.ata.advertising.service.model.GeneratedAdvertisement;
import com.amazon.ata.advertising.service.model.RequestContext;
import com.amazon.ata.advertising.service.targeting.TargetingEvaluator;
import com.amazon.ata.advertising.service.targeting.TargetingGroup;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.stream.Collectors;
import javax.inject.Inject;

/**
 * This class is responsible for picking the advertisement to be rendered.
 */
public class AdvertisementSelectionLogic {

    private static final Logger LOG = LogManager.getLogger(AdvertisementSelectionLogic.class);

    private final ReadableDao<String, List<AdvertisementContent>> contentDao;
    private final ReadableDao<String, List<TargetingGroup>> targetingGroupDao;
    private Random random = new Random();

    /**
     * Constructor for AdvertisementSelectionLogic.
     *
     * @param contentDao        Source of advertising content.
     * @param targetingGroupDao Source of targeting groups for each advertising content.
     */
    @Inject
    public AdvertisementSelectionLogic(ReadableDao<String, List<AdvertisementContent>> contentDao,
                                       ReadableDao<String, List<TargetingGroup>> targetingGroupDao) {
        this.contentDao = contentDao;
        this.targetingGroupDao = targetingGroupDao;
    }

    /**
     * Setter for Random class.
     *
     * @param random generates random number used to select advertisements.
     */
    public void setRandom(Random random) {
        this.random = random;
    }

    /**
     * Gets all of the content and metadata for the marketplace and determines which content can be shown.  Returns the
     * eligible content with the highest click through rate.  If no advertisement is available or eligible, returns an
     * EmptyGeneratedAdvertisement.
     *
     * @param customerId    - the customer to generate a custom advertisement for
     * @param marketplaceId - the id of the marketplace the advertisement will be rendered on
     * @return an advertisement customized for the customer id provided, or an empty advertisement if one could
     * not be generated.
     */
    public GeneratedAdvertisement selectAdvertisement(String customerId, String marketplaceId) {
        if (StringUtils.isEmpty(marketplaceId)) {
            LOG.warn("MarketplaceId cannot be null or empty. Returning empty ad.");
            return new EmptyGeneratedAdvertisement();
        }

        List<AdvertisementContent> contents = contentDao.get(marketplaceId);
        if (CollectionUtils.isEmpty(contents)) {
            LOG.warn("No advertisement contents found for marketplaceId: " + marketplaceId);
            return new EmptyGeneratedAdvertisement();
        }

        TargetingEvaluator evaluator = new TargetingEvaluator(new RequestContext(customerId, marketplaceId));

        // Map to store advertisements by their max click-through rate
        TreeMap<Double, AdvertisementContent> adsByCTR = new TreeMap<>(Comparator.reverseOrder());

        for (AdvertisementContent content : contents) {
            if (StringUtils.isBlank(content.getRenderableContent())) {
                continue;
            }

            List<TargetingGroup> targetingGroups = targetingGroupDao.get(content.getContentId());
            if (CollectionUtils.isEmpty(targetingGroups)) {
                continue;
            }

            // Find the maximum click-through rate among the eligible targeting groups
            OptionalDouble maxCTR = targetingGroups.stream()
                    .filter(group -> evaluator.evaluate(group).isTrue())
                    .mapToDouble(TargetingGroup::getClickThroughRate)
                    .max();

            if (maxCTR.isPresent()) {
                adsByCTR.put(maxCTR.getAsDouble(), content);
            }
        }

        // Return an empty ad if no eligible ads are found
        if (adsByCTR.isEmpty()) {
            LOG.warn("No eligible advertisements found for customerId: " + customerId +
                    " in marketplaceId: " + marketplaceId);
            return new EmptyGeneratedAdvertisement();
        }

        // Return the ad with the highest click-through rate
        return new GeneratedAdvertisement(adsByCTR.firstEntry().getValue());
    }
}