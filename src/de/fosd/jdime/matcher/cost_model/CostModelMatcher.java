package de.fosd.jdime.matcher.cost_model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import de.fosd.jdime.common.Artifact;
import de.fosd.jdime.common.ArtifactList;
import de.fosd.jdime.common.Artifacts;
import de.fosd.jdime.common.MergeContext;
import de.fosd.jdime.common.Tuple;
import de.fosd.jdime.matcher.MatcherInterface;
import de.fosd.jdime.matcher.matching.Matching;
import de.fosd.jdime.matcher.matching.Matchings;
import org.apache.commons.math3.random.RandomGenerator;

import static de.fosd.jdime.matcher.cost_model.Bounds.BY_LOWER_UPPER;
import static java.lang.Integer.toHexString;
import static java.lang.System.identityHashCode;
import static java.util.Comparator.comparing;
import static java.util.logging.Level.FINER;
import static java.util.logging.Level.FINEST;
import static java.util.stream.Collectors.summingDouble;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static java.util.stream.Stream.concat;

public class CostModelMatcher<T extends Artifact<T>> implements MatcherInterface<T> {

    private static final Logger LOG = Logger.getLogger(CostModelMatcher.class.getCanonicalName());

    @FunctionalInterface
    public interface SimpleWeightFunction<T extends Artifact<T>> {

        float weigh(CMMatching<T> matching);
    }

    @FunctionalInterface
    public interface WeightFunction<T extends Artifact<T>> {

        float weigh(CMMatching<T> matching, float quantity);
    }

    /**
     * The return type of {@link #objective(CMMatchings, CMParameters)} containing the value of the objective
     * function and the exact cost of the newly proposed set of <code>CMMatching</code>s.
     */
    private final class ObjectiveValue {

        public final double objValue;
        public final float matchingsCost;

        public ObjectiveValue(double objValue, float matchingsCost) {
            this.objValue = objValue;
            this.matchingsCost = matchingsCost;
        }
    }

    /**
     * The return type of {@link #acceptanceProb(double, CMMatchings, CMParameters)} containing the probability
     * of the newly proposed set of <code>CMMatching</code>s being accepted for the next iteration and the
     * <code>ObjectiveValue</code> for the proposed matchings.
     */
    private final class AcceptanceProbability {

        public final double acceptanceProbability;
        public final ObjectiveValue mHatObjectiveValue;

        public AcceptanceProbability(double acceptanceProbability, ObjectiveValue mHatObjectiveValue) {
            this.acceptanceProbability = acceptanceProbability;
            this.mHatObjectiveValue = mHatObjectiveValue;
        }
    }

    public float cost(MergeContext context, Matchings<T> matchings, T left, T right) {

        if (matchings.isEmpty()) {
            return 0;
        }

        Set<T> leftUnmatched = new LinkedHashSet<>(Artifacts.dfs(left));
        Set<T> rightUnmatched = new LinkedHashSet<>(Artifacts.dfs(right));

        CMMatchings<T> cmMatchings = new CMMatchings<>(left, right);

        for (Matching<T> matching : matchings) {
            cmMatchings.add(new CMMatching<>(matching.getLeft(), matching.getRight()));

            leftUnmatched.remove(matching.getLeft());
            rightUnmatched.remove(matching.getRight());
        }

        for (T l : leftUnmatched) {
            cmMatchings.add(new CMMatching<>(l, null));
        }

        for (T r : rightUnmatched) {
            cmMatchings.add(new CMMatching<>(null, r));
        }

        return cost(cmMatchings, new CMParameters<>(context));
    }

    /**
     * Returns the exact cost of the given <code>matchings</code>. This assumes that <code>matchings</code> contains
     * for every node in the left and right tree exactly one <code>CMMatching</code> containing the node.
     * The exact cost computed for every <code>CMMatching</code> can be retrieved using
     * ({@link CMMatching#getExactCost()} after this call.
     *
     * @param matchings
     *         the <code>CMMatchings</code>s to evaluate
     * @param parameters
     *          the <code>CMParameters</code> to use
     * @return the cost based on the weight functions in <code>parameters</code>
     */
    private float cost(CMMatchings<T> matchings, CMParameters<T> parameters) {

        if (!matchings.sane()) {
            throw new IllegalArgumentException("The given list of matchings has an invalid format. A list of " +
                    "matchings where every artifact from the left and right tree occurs in exactly one matching is " +
                    "required. Matchings matching artifacts that do not occur in the left or right tree are not " +
                    "allowed.");
        }

        if (matchings.isEmpty()) {
            return 0;
        }

        if (parameters.parallel) {
            matchings.parallelStream().forEach(m -> cost(m, matchings, parameters));
        } else {
            matchings.forEach(m -> cost(m, matchings, parameters));
        }

        float sumCost = matchings.stream().collect(summingDouble(CMMatching::getExactCost)).floatValue();
        sumCost *= (1.0f / (matchings.left.getTreeSize() + matchings.right.getTreeSize()));

        parameters.clearExactCaches();

        return sumCost;
    }

    /**
     * Sets the exact cost ({@link CMMatching#setExactCost(float)}) of the given <code>matching</code> based on
     * the given set of <code>matchings</code>.
     *
     *  @param matching
     *         the <code>CMMatching</code> to compute the cost for
     * @param matchings
     *         the complete <code>CMMatching</code>s
     * @param parameters
     *         the <code>CMParameters</code> to use
     */
    private void cost(CMMatching<T> matching, CMMatchings<T> matchings, CMParameters<T> parameters) {

        if (matching.isNoMatch()) {
            matching.setExactCost(parameters.wn);
            return;
        }

        float cR = renamingCost(matching, parameters);
        float cA = ancestryViolationCost(matching, matchings, parameters);
        float cS = siblingGroupBreakupCost(matching, matchings, parameters);
        float cO = orderingCost(matching, matchings, parameters);

        matching.setExactCost(cR + cA + cS + cO);
    }

    /**
     * Returns the cost for renaming the node. The cost will be zero if the <code>Artifact</code>s match according to
     * {@link Artifact#matches(Artifact)}, otherwise it is determined by the set renaming weight function
     * in <code>parameters</code>.
     *
     * @param matching
     *         the <code>CMMatching</code> to compute the cost for
     * @return the exact renaming cost of the <code>matching</code>
     */
    private float renamingCost(CMMatching<T> matching, CMParameters<T> parameters) {
        if (matching.m.matches(matching.n)) {
            return 0;
        } else {
            return parameters.wr.weigh(matching);
        }
    }

    private float ancestryViolationCost(CMMatching<T> matching, CMMatchings<T> matchings, CMParameters<T> parameters) {
        int numM = numAncestryViolatingChildren(matching.m, matching.n, matchings, parameters);
        int numN = numAncestryViolatingChildren(matching.n, matching.m, matchings, parameters);

        return parameters.wa.weigh(matching, numM + numN);
    }

    private int numAncestryViolatingChildren(T m, T n, CMMatchings<T> matchings, CMParameters<T> parameters) {
        ArtifactList<T> mChildren = m.getChildren();
        ArtifactList<T> nChildren = n.getChildren();

        Predicate<T> filter = a -> a != null && !nChildren.contains(a);

        return (int) mChildren.stream().map(mChild -> image(mChild, matchings, parameters)).filter(filter).count();
    }

    private float siblingGroupBreakupCost(CMMatching<T> matching, CMMatchings<T> matchings, CMParameters<T> parameters) {
        List<T> dMm, iMm;
        Set<T> fMm;
        List<T> dMn, iMn;
        Set<T> fMn;
        float mCost;
        float nCost;

        dMm = siblingDivergentSubset(matching.m, matching.n, matchings, parameters);

        if (dMm.isEmpty()) {
            mCost = 0;
        } else {
            iMm = siblingInvariantSubset(matching.m, matching.n, matchings, parameters);
            fMm = distinctSiblingFamilies(matching.m, matchings, parameters);
            mCost = (float) dMm.size() / (iMm.size() * fMm.size());
        }

        dMn = siblingDivergentSubset(matching.n, matching.m, matchings, parameters);

        if (dMn.isEmpty()) {
            nCost = 0;
        } else {
            iMn = siblingInvariantSubset(matching.n, matching.m, matchings, parameters);
            fMn = distinctSiblingFamilies(matching.n, matchings, parameters);
            nCost = (float) dMn.size() / (iMn.size() * fMn.size());
        }

        return parameters.ws.weigh(matching, mCost + nCost);
    }

    private List<T> siblingInvariantSubset(T m, T n, CMMatchings<T> matchings, CMParameters<T> parameters) {
        List<T> mSiblings = siblings(m, matchings, parameters);
        List<T> nSiblings = siblings(n, matchings, parameters);

        return mSiblings.stream().filter(s -> nSiblings.contains(image(s, matchings, parameters))).collect(toList());
    }

    private List<T> siblingDivergentSubset(T m, T n, CMMatchings<T> matchings, CMParameters<T> parameters) {
        List<T> inv = siblingInvariantSubset(m, n, matchings, parameters);
        return siblings(m, matchings, parameters).stream().filter(sibling -> !inv.contains(sibling) && image(sibling, matchings, parameters) != null).collect(toList());
    }

    private Set<T> distinctSiblingFamilies(T m, CMMatchings<T> matchings, CMParameters<T> parameters) {
        Function<T, T> image = mChild -> image(mChild, matchings, parameters);
        Predicate<T> notNull = t -> t != null;
        Function<T, T> getParent = Artifact::getParent;

        return siblings(m, matchings, parameters).stream().map(image).filter(notNull).map(getParent).collect(toSet());
    }

    private float orderingCost(CMMatching<T> matching, CMMatchings<T> matchings, CMParameters<T> parameters) {
        Stream<T> leftSiblings = otherSiblings(matching.m, matchings, parameters).stream();
        Stream<T> rightSiblings = otherSiblings(matching.n, matchings, parameters).stream();
        Stream<CMMatching<T>> s = concat(leftSiblings, rightSiblings).map(a -> matching(a, matchings, parameters))
                                                                     .filter(m -> !m.isNoMatch()).distinct();

        if (s.anyMatch(toCheck -> violatesOrdering(toCheck, matching, matchings, parameters))) {
            return parameters.wo.weigh(matching);
        } else {
            return 0;
        }
    }

    private boolean violatesOrdering(CMMatching<T> toCheck, CMMatching<T> matching, CMMatchings<T> matchings, CMParameters<T> parameters) {
        Tuple<T, T> leftSides = lca(toCheck.m, matching.m, parameters, matchings);
        Tuple<T, T> rightSides = lca(toCheck.n, matching.n, parameters, matchings);
        List<T> leftSiblings = siblings(leftSides.x, matchings, parameters);
        List<T> rightSiblings = siblings(rightSides.x, matchings, parameters);

        int leftXi = leftSiblings.indexOf(leftSides.x);
        int leftYi = leftSiblings.indexOf(leftSides.y);
        int rightXi = rightSiblings.indexOf(rightSides.x);
        int rightYi = rightSiblings.indexOf(rightSides.y);
        
        if (leftXi < leftYi) {
            return rightXi > rightYi;
        } else if (leftXi > leftYi) {
            return rightXi < rightYi;
        }

        return false; // TODO weird case, maybe true is better?
    }

    /**
     * Returns the path from the given <code>artifact</code> to the root node of the tree it is a part of.
     *
     * @param artifact
     *         the <code>Artifact</code> to return the path for
     * @return the path represented by a list of <code>Artifact</code>s beginning with <code>artifact</code> and ending
     *          with the root of the tree
     */
    private List<T> pathToRoot(T artifact) {
        List<T> path = new ArrayList<>();

        do {
            path.add(artifact);
            artifact = artifact.getParent();
        } while (artifact != null);

        return path;
    }

    /**
     * Finds the lowest pair of (possibly different) ancestors of <code>a</code> and <code>b</code> that are part of the
     * same sibling group.
     *
     * @param a
     *         the first <code>Artifact</code>
     * @param b
     *         the second <code>Artifact</code>
     * @param parameters
     *         the <code>CMParameters</code> to use
     * @param matchings
     * @return the ancestor of the first <code>Artifact</code> in the first position, that of the second in the second
     *          position
     */
    private Tuple<T, T> lca(T a, T b, CMParameters<T> parameters, CMMatchings<T> matchings) {
        return parameters.lcaCache.computeIfAbsent(Tuple.of(a, b), ab -> {
            Tuple<T, T> ba = Tuple.of(b, a);

            if (parameters.lcaCache.containsKey(ba)) {
                Tuple<T, T> baLCS = parameters.lcaCache.get(ba);
                return Tuple.of(baLCS.y, baLCS.x);
            }

            if (siblings(a, matchings, parameters).contains(b)) {
                return ab;
            }

            List<T> aPath = pathToRoot(a);
            List<T> bPath = pathToRoot(b);
            ListIterator<T> aIt = aPath.listIterator(aPath.size());
            ListIterator<T> bIt = bPath.listIterator(bPath.size());
            T l, r;

            do {
                l = aIt.previous();
                r = bIt.previous();
            } while (l == r && (aIt.hasPrevious() && bIt.hasPrevious()));

            return Tuple.of(l, r);
        });
    }

    /**
     * Finds the (first) <code>CMMatching</code> in <code>matchings</code> containing the given
     * <code>artifact</code>.
     *
     * @param artifact
     *         the <code>Artifact</code> for which the containing <code>CMMatching</code> is to be returned
     * @param matchings
     *         the current matchings
     * @param parameters
     *         the <code>CMParameters</code> to use
     * @return the <code>CMMatching</code> containing the <code>artifact</code>
     * @throws NoSuchElementException
     *         if no <code>CMMatching</code> containing <code>artifact</code> can be found in
     *         <code>matchings</code>
     */
    private CMMatching<T> matching(T artifact, CMMatchings<T> matchings, CMParameters<T> parameters) {

        return parameters.exactContainsCache.computeIfAbsent(artifact, a ->
            matchings.stream().filter(m -> m.contains(a)).findFirst().orElseThrow(() ->
                new NoSuchElementException("No matching containing " + artifact + " found.")
            )
        );
    }

    /**
     * Finds the (first) <code>CMMatching</code> in <code>matchings</code> containing the given
     * <code>artifact</code> and returns the other <code>Artifact</code> in the <code>CMMatching</code>.
     *
     * @param artifact
     *         the <code>Artifact</code> whose image is to be returned
     * @param matchings
     *         the current matchings
     * @return the matching partner of <code>artifact</code> in the given <code>matchings</code>
     * @throws NoSuchElementException
     *         if no <code>CMMatching</code> containing <code>artifact</code> can be found in
     *         <code>matchings</code>
     */
    private T image(T artifact, CMMatchings<T> matchings, CMParameters<T> parameters) {
        return matching(artifact, matchings, parameters).other(artifact);
    }

    /**
     * Sets the bounds ({@link CMMatching#setCostBounds(Bounds)}) for the cost of all current matchings.
     *
     * @param currentMatchings
     *         the current <code>CMMatchings</code>s being considered
     * @param parameters
     *         the <code>CMParameters</code> to use
     */
    private void boundCost(CMMatchings<T> currentMatchings, CMParameters<T> parameters) {
        LOG.finer(() -> "Bounding " + currentMatchings.size() + " matchings.");

        AtomicInteger mCount = LOG.isLoggable(FINEST) ? new AtomicInteger() : null;
        Consumer<CMMatching<T>> mPeek = m -> LOG.finest(() -> "Done with matching " + mCount.getAndIncrement() + " " + m);

        if (parameters.parallel) {
            currentMatchings.parallelStream().peek(mPeek).forEach(m -> boundCost(m, currentMatchings, parameters));
        } else {
            currentMatchings.stream().peek(mPeek).forEach(m -> boundCost(m, currentMatchings, parameters));
        }

        parameters.clearBoundCaches();
    }

    /**
     * Sets the bounds ({@link CMMatching#setCostBounds(Bounds)}) for the cost of the given <code>matching</code>
     * based on the given <code>currentMatchings</code>.
     *
     * @param matching
     *         the <code>CMMatching</code> whose costs are to be bounded
     * @param currentMatchings
     *         the current <code>CMMatchings</code>s being considered
     * @param parameters
     *         the <code>CMParameters</code> to use
     */
    private void boundCost(CMMatching<T> matching, CMMatchings<T> currentMatchings, CMParameters<T> parameters) {

        if (matching.isNoMatch()) {
            matching.setBounds(parameters.wn, parameters.wn);
            return;
        }

        float cR = renamingCost(matching, parameters);
        Bounds cABounds = boundAncestryViolationCost(matching, currentMatchings, parameters);
        Bounds cSBounds = boundSiblingGroupBreakupCost(matching, currentMatchings, parameters);
        Bounds cOBounds = boundOrderingCost(matching, currentMatchings, parameters);

        float lower = cR + cABounds.getLower() + cSBounds.getLower() + cOBounds.getLower();
        float upper = cR + cABounds.getUpper() + cSBounds.getUpper() + cOBounds.getUpper();

        matching.setBounds(lower, upper);
    }

    private Bounds boundAncestryViolationCost(CMMatching<T> matching, CMMatchings<T> currentMatchings, CMParameters<T> parameters) {
        T m = matching.m;
        T n = matching.n;

        Stream<T> mLower = m.getChildren().stream().filter(mChild -> ancestryIndicator(mChild, n, currentMatchings, false, parameters));
        Stream<T> nLower = n.getChildren().stream().filter(nChild -> ancestryIndicator(nChild, m, currentMatchings, false, parameters));

        Stream<T> mUpper = m.getChildren().stream().filter(mChild -> ancestryIndicator(mChild, n, currentMatchings, true, parameters));
        Stream<T> nUpper = n.getChildren().stream().filter(nChild -> ancestryIndicator(nChild, m, currentMatchings, true, parameters));

        int lowerBound = (int) (mLower.count() + nLower.count());
        int upperBound = (int) (mUpper.count() + nUpper.count());

        return new Bounds(parameters.wa.weigh(matching, lowerBound), parameters.wa.weigh(matching, upperBound));
    }

    private boolean ancestryIndicator(T child, T n, CMMatchings<T> currentMatchings, boolean upper, CMParameters<T> parameters) {

        if (upper) {
            Predicate<CMMatching<T>> indicator = match -> {
                T partner = match.other(child);
                return !(partner == null || n.getChildren().contains(partner));
            };

            return containing(child, currentMatchings, parameters).stream().anyMatch(indicator);
        } else {
            Predicate<CMMatching<T>> indicator = match -> {
                T partner = match.other(child);
                return partner == null || n.getChildren().contains(partner);
            };

            return containing(child, currentMatchings, parameters).stream().noneMatch(indicator);
        }
    }

    private Bounds boundSiblingGroupBreakupCost(CMMatching<T> matching, CMMatchings<T> currentMatchings, CMParameters<T> parameters) {
        T m = matching.m;
        T n = matching.n;

        float mnLower, nmLower, lower, mnUpper, nmUpper, upper;

        Bounds dMN = boundDistinctSibling(m, n, currentMatchings, parameters);
        Bounds dNM = boundDistinctSibling(n, m, currentMatchings, parameters);

        if (dMN.getLower() != 0 || dMN.getUpper() != 0) {
            Bounds iMN = boundInvariantSiblings(m, n, currentMatchings, parameters);
            mnLower = dMN.getLower() / (iMN.getUpper() * (dMN.getLower() + 1));
            mnUpper = dMN.getUpper() / iMN.getLower();
        } else {
            mnLower = 0;
            mnUpper = 0;
        }

        if (dNM.getLower() != 0 || dNM.getUpper() != 0) {
            Bounds iNM = boundInvariantSiblings(n, m, currentMatchings, parameters);
            nmLower = dNM.getLower() / (iNM.getUpper() * (dNM.getLower() + 1));
            nmUpper = dNM.getUpper() / iNM.getLower();
        } else {
            nmLower = 0;
            nmUpper = 0;
        }

        lower = parameters.ws.weigh(matching, mnLower + nmLower);
        upper = parameters.ws.weigh(matching, (mnUpper + nmUpper) / 2);

        return new Bounds(lower, upper);
    }

    private Bounds boundDistinctSibling(T m, T n, CMMatchings<T> currentMatchings, CMParameters<T> parameters) {
        long lower = otherSiblings(m, currentMatchings, parameters).stream().filter(mSib -> distinctSiblingIndicator(mSib, n, currentMatchings, false, parameters)).count();
        long upper = otherSiblings(m, currentMatchings, parameters).stream().filter(mSib -> distinctSiblingIndicator(mSib, n, currentMatchings, true, parameters)).count();

        return new Bounds(lower, upper);
    }

    private boolean distinctSiblingIndicator(T sibling, T n, CMMatchings<T> currentMatchings, boolean upper, CMParameters<T> parameters) {

        if (upper) {
            Predicate<CMMatching<T>> indicator = match -> {
                T partner = match.other(sibling);
                return !(partner == null || otherSiblings(n, currentMatchings, parameters).contains(partner));
            };

            return containing(sibling, currentMatchings, parameters).stream().anyMatch(indicator);
        } else {
            Predicate<CMMatching<T>> indicator = match -> {
                T partner = match.other(sibling);
                return partner == null || otherSiblings(n, currentMatchings, parameters).contains(partner);
            };

            return containing(sibling, currentMatchings, parameters).stream().noneMatch(indicator);
        }
    }

    private Bounds boundInvariantSiblings(T m, T n, CMMatchings<T> currentMatchings, CMParameters<T> parameters) {
        long lower = otherSiblings(m, currentMatchings, parameters).stream().filter(mChild -> invariantSiblingIndicator(mChild, n, currentMatchings, false, parameters)).count();
        long upper = otherSiblings(m, currentMatchings, parameters).stream().filter(mChild -> invariantSiblingIndicator(mChild, n, currentMatchings, true, parameters)).count();

        return new Bounds(lower + 1, upper + 1);
    }

    private boolean invariantSiblingIndicator(T child, T n, CMMatchings<T> currentMatchings, boolean upper, CMParameters<T> parameters) {
        Predicate<CMMatching<T>> indicator = match -> otherSiblings(n, currentMatchings, parameters).contains(match.other(child));

        if (upper) {
            return containing(child, currentMatchings, parameters).stream().anyMatch(indicator);
        } else {
            return containing(child, currentMatchings, parameters).stream().allMatch(indicator);
        }
    }

    private Bounds boundOrderingCost(CMMatching<T> matching, CMMatchings<T> currentMatchings, CMParameters<T> parameters) {
        float lower, upper;

        Stream<T> siblings = concat(otherSiblings(matching.m, currentMatchings, parameters).stream(), otherSiblings(matching.n, currentMatchings, parameters).stream());

        boolean orderingPossible = siblings.allMatch(sib ->
            containing(sib, currentMatchings, parameters).stream().anyMatch(match ->
                match.isNoMatch() || !violatesOrdering(match, matching, currentMatchings, parameters)
            )
        );

        if (!orderingPossible) {
            lower = parameters.wo.weigh(matching);
            upper = lower;
        } else {
            lower = 0;

            siblings = concat(otherSiblings(matching.m, currentMatchings, parameters).stream(), otherSiblings(matching.n, currentMatchings, parameters).stream());

            boolean violationPossible = siblings.anyMatch(sib ->
                containing(sib, currentMatchings, parameters).stream().anyMatch(match ->
                    !match.isNoMatch() && violatesOrdering(match, matching, currentMatchings, parameters)
                )
            );

            upper = violationPossible ? parameters.wo.weigh(matching) : 0;
        }

        return new Bounds(lower, upper);
    }

    /**
     * Returns a new <code>List</code> containing the children of the parent of <code>artifact</code> or an empty
     * <code>List</code> for the root node. This includes the <code>artifact</code> itself.
     *
     * @param artifact
     *         the <code>Artifact</code> whose siblings are to be returned
     * @param parameters
     *         the <code>CMParameters</code> to use
     * @return the siblings of the given <code>artifact</code>
     */
    private List<T> siblings(T artifact, CMMatchings<T> matchings, CMParameters<T> parameters) {
        return parameters.siblingCache.computeIfAbsent(artifact, a -> {
            List<T> siblings;

            if (artifact == matchings.left || artifact == matchings.right) {
                siblings = new ArrayList<>(Collections.singleton(a));
            } else {
                T parent = a.getParent();
                siblings = parent.getChildren()
                                 .stream()
                                 .filter(s -> s != a && parameters.siblingCache.containsKey(s))
                                 .map(s -> parameters.siblingCache.get(s)).findFirst()
                                 .orElseGet(() -> new ArrayList<>(parent.getChildren()));
            }

            return siblings;
        });
    }

    /**
     * Returns the siblings of <code>artifact</code> as in {@link #siblings(Artifact, CMMatchings, CMParameters)} but
     * does not include <code>artifact</code> itself.
     *
     * @param artifact
     *         the <code>Artifact</code> whose siblings are to be returned
     * @param matchings
     *@param parameters
     *         the <code>CMParameters</code> to use  @return the siblings of the given <code>artifact</code>
     */
    private List<T> otherSiblings(T artifact, CMMatchings<T> matchings, CMParameters<T> parameters) {
        return parameters.otherSiblingsCache.computeIfAbsent(artifact, a -> {
            List<T> siblings = new ArrayList<>(siblings(a, matchings, parameters));
            siblings.remove(a);

            return siblings;
        });
    }

    private List<CMMatching<T>> containing(T artifact, CMMatchings<T> currentMatchings, CMParameters<T> parameters) {
        return parameters.boundContainsCache.computeIfAbsent(artifact, a ->
            currentMatchings.stream().filter(m -> m.contains(a)).collect(toList())
        );
    }

    @Override
    public Matchings<T> match(MergeContext context, T left, T right) {
        return match(context, left, right, new CMMatchings<>(left, right));
    }

    public Matchings<T> match(MergeContext context, T left, T right, Matchings<T> preFixed) {
        CMMatchings<T> cmPreFixed = new CMMatchings<>(left, right);

        for (Matching<T> matching : preFixed.optimized()) {
            cmPreFixed.add(new CMMatching<>(matching.getLeft(), matching.getRight()));
        }

        return match(context, left, right, cmPreFixed);
    }

    private Matchings<T> match(MergeContext context, T left, T right, CMMatchings<T> preFixed) {
        CMParameters<T> parameters = new CMParameters<>(context);

        LOG.fine("Matching " + left + " and " + right + " using the " + getClass().getSimpleName());

        CMMatchings<T> m = initialize(preFixed, parameters);
        ObjectiveValue mObjVal = objective(m, parameters);

        CMMatchings<T> lowest = m;
        float lowestCost = mObjVal.matchingsCost;

        for (int i = 0; i < context.getCostModelIterations(); i++) {
            CMMatchings<T> mHat = propose(m, preFixed, parameters);
            AcceptanceProbability mHatAccProb = acceptanceProb(mObjVal.objValue, mHat, parameters);

            if (chance(parameters.rng, mHatAccProb.acceptanceProbability)) {

                log(FINER, mHat, () -> "Accepting the matchings.");

                m = mHat;
                mObjVal = mHatAccProb.mHatObjectiveValue;
            }

            if (mHatAccProb.mHatObjectiveValue.matchingsCost < lowestCost) {

                lowest = mHat;
                lowestCost = mHatAccProb.mHatObjectiveValue.matchingsCost;

                float finalLowestCost = lowestCost;
                log(FINER, mHat, () -> "New lowest cost matchings with cost " + finalLowestCost + " found.");
            }

            LOG.fine("End of iteration " + i);
        }

        LOG.fine(() -> "Matching ended after " + context.getCostModelIterations() + " iterations.");

        return convert(lowest);
    }

    /**
     * Returns <code>true</code> with a probability of <code>p</code>.
     *
     * @param rng
     *         the PRNG to sample from
     * @param p
     *         a number between 0.0 and 1.0
     * @return true or false depending on the next double returned by the PRNG
     */
    boolean chance(RandomGenerator rng, double p) {
        return rng.nextDouble() < p;
    }

    /**
     * Converts a <code>List</code> of <code>CMMatching</code>s to an equivalent <code>Set</code> of
     * <code>Matching</code>s.
     *
     * @param matchings
     *         the <code>CMMatching</code>s to convert
     * @return the resulting <code>Matchings</code>
     */
    private Matchings<T> convert(CMMatchings<T> matchings) {
        Map<T, T> mMap = matchings.asMap();

        Function<CMMatching<T>, Matching<T>> toMatching = m -> {
            Set<T> ls = new HashSet<>(Artifacts.dfs(m.m));
            Set<T> rs = new HashSet<>(Artifacts.dfs(m.n));
            int score = (int) ls.stream().filter(a -> rs.contains(mMap.get(a))).count();

            Matching<T> matching = new Matching<>(m.m, m.n, score);
            matching.setAlgorithm(CostModelMatcher.class.getSimpleName());
            return matching;
        };

        return matchings.stream().filter(m -> !m.isNoMatch()).map(toMatching)
                                 .collect(Matchings::new, Matchings::add, Matchings::addAll);
    }

    private CMMatchings<T> propose(CMMatchings<T> m, CMMatchings<T> preFixed, CMParameters<T> parameters) {
        CMMatchings<T> mVariable = new CMMatchings<>(m, m.left, m.right);
        mVariable.removeAll(preFixed);

        int j;

        if (parameters.fixRandomPercentage) {
            int lower = (int) (parameters.fixLower * mVariable.size());
            int upper = (int) (parameters.fixUpper * mVariable.size());

            Collections.shuffle(mVariable, parameters.rng); // TODO a switch to turn this off
            j = intFromRange(lower, upper, parameters);
        } else {
            //TODO sort by exact cost?
            Collections.sort(mVariable, Comparator.comparing(CMMatching::getExactCost));
            j = parameters.rng.nextInt(mVariable.size());
        }

        CMMatchings<T> fixed = new CMMatchings<>(mVariable.subList(0, j), m.left, m.right);

        log(FINER, m, () -> "Fixing the first " + j + "variable matchings from the last iteration.");
        log(FINEST, m, () -> "They are: " + fixed);

        fixed.addAll(preFixed);

        CMMatchings<T> proposition = complete(fixed, parameters);

        log(FINER, proposition, () -> "Proposing matchings for the next iteration.");
        log(FINEST, proposition, () -> "Proposition is: " + proposition);

        return proposition;
    }

    private int intFromRange(int lower, int upper, CMParameters<T> parameters) {
        return lower + (int) (parameters.rng.nextFloat() * ((upper - lower) + 1));
    }

    private CMMatchings<T> initialize(CMMatchings<T> preFixed, CMParameters<T> parameters) {
        CMMatchings<T> initial = complete(preFixed, parameters);

        log(FINER, initial, () -> "Initial set of matchings assembled.");
        log(FINEST, initial, () -> "Initial set is: " + initial);

        return initial;
    }

    private CMMatchings<T> complete(CMMatchings<T> fixedMatchings, CMParameters<T> parameters) {
        CMMatchings<T> current = completeBipartiteGraph(fixedMatchings.left, fixedMatchings.right, parameters);
        CMMatchings<T> fixed = new CMMatchings<>(fixedMatchings, fixedMatchings.left, fixedMatchings.right);

        fixed.forEach(m -> prune(m, current));

        while (fixed.size() != current.size()) {

            boundCost(current, parameters);
            Collections.sort(current, comparing(CMMatching::getCostBounds, BY_LOWER_UPPER));

            CMMatchings<T> available = new CMMatchings<>(current, current.left, current.right);
            available.removeAll(fixed);

            int i;
            do {
                i = parameters.assignDist.sample();
            } while (i >= available.size());

            CMMatching<T> matching = available.get(i);

            /* TODO
             * "If the candidate edge can be fixed (?) and at least one complete matching still exists (???)"
             *
             * if (???) {
             *     continue;
             * }
             */

            fixed.add(matching);
            prune(matching, current);
        }

        return fixed;
    }

    private void prune(CMMatching<T> matching, CMMatchings<T> g) {

        for (ListIterator<CMMatching<T>> it = g.listIterator(); it.hasNext();) {
            CMMatching<T> current = it.next();
            boolean neq = !matching.equals(current);

            if (neq && ((matching.m != null && matching.m == current.m) || (matching.n != null && matching.n == current.n))) {
                it.remove();
            }
        }
    }

    private CMMatchings<T> completeBipartiteGraph(T left, T right, CMParameters<T> parameters) {
        List<T> leftNodes = Artifacts.bfs(left);
        List<T> rightNodes = Artifacts.bfs(right);

        // add the "No Match" node
        leftNodes.add(null);
        rightNodes.add(null);

        CMMatchings<T> bipartiteGraph = new CMMatchings<>(left, right);

        for (T lNode : leftNodes) {
            for (T rNode : rightNodes) {

                if (lNode != null || rNode != null) {
                    bipartiteGraph.add(new CMMatching<>(lNode, rNode));
                }
            }
        }

        Collections.shuffle(bipartiteGraph, parameters.rng);
        return bipartiteGraph;
    }

    private ObjectiveValue objective(CMMatchings<T> matchings, CMParameters<T> parameters) {
        float cost = cost(matchings, parameters);
        double objVal = Math.exp(-(parameters.beta * cost));

        log(FINER, matchings, () -> "Cost of matchings is " + cost);
        log(FINER, matchings, () -> "Objective function value for matchings is " + objVal);

        return new ObjectiveValue(objVal, cost);
    }

    private AcceptanceProbability acceptanceProb(double mObjectiveValue, CMMatchings<T> mHat, CMParameters<T> parameters) {
        ObjectiveValue mHatObjectiveValue = objective(mHat, parameters);
        double acceptanceProb = Math.min(1, mHatObjectiveValue.objValue / mObjectiveValue);

        log(FINER, mHat, () -> "Acceptance probability for matchings is " + acceptanceProb);

        return new AcceptanceProbability(acceptanceProb, mHatObjectiveValue);
    }

    /**
     * Returns the hexadecimal identity hash code of the given <code>Object</code> as a <code>String</code>.
     *
     * @param o
     *         the <code>Object</code> to return the <code>String</code> id for
     * @return the <code>String</code> id
     */
    private String id(Object o) {
        return toHexString(identityHashCode(o));
    }

    /**
     * Logs the given <code>msg</code> using the {@link #LOG} and prepends the {@link #id(Object)} of the given
     * matchings.
     *
     * @param level
     *         the level to log at
     * @param matchings
     *         the matchings the message concerns
     * @param msg
     *         the message to log
     */
    private void log(Level level, CMMatchings<T> matchings, Supplier<String> msg) {
        LOG.log(level, () -> String.format("%-10s%s", id(matchings), msg.get()));
    }
}
