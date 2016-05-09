/**
 * Copyright (C) 2013-2014 Olaf Lessenich
 * Copyright (C) 2014-2015 University of Passau, Germany
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 *
 * Contributors:
 *     Olaf Lessenich <lessenic@fim.uni-passau.de>
 *     Georg Seibt <seibt@fim.uni-passau.de>
 */
package de.fosd.jdime.matcher.ordered;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import de.fosd.jdime.common.Artifact;
import de.fosd.jdime.common.MergeContext;
import de.fosd.jdime.common.UnorderedTuple;
import de.fosd.jdime.matcher.MatcherInterface;
import de.fosd.jdime.matcher.matching.Matching;
import de.fosd.jdime.matcher.matching.Matchings;

/**
 * The <code>EqualityMatcher</code> can be used to compute <code>Matchings</code> for identical trees.
 * It traverses two ordered trees in post order and produces respective <code>Matchings</code>.
 * <p>
 * <code>EqualityMatcher</code> does not use its parent matcher to dispatch match() calls, and uses its own
 * implementation instead.
 * <p>
 * Usage:<br/>
 * To check whether the trees are equal, extract the <code>Matching</code> with the highest score and compare it
 * with the size of the trees.
 *
 * @param <T> type of <code>Artifact</code>
 * @author Olaf Lessenich
 */
public class EqualityMatcher<T extends Artifact<T>> extends OrderedMatcher<T> {

    private static final String ID = EqualityMatcher.class.getSimpleName();
    private static final Collector<Integer, ?, Integer> SUM_IDENTITY = Collectors.summingInt(i -> i);

    private Set<UnorderedTuple<T, T>> didNotMatch;

    /**
     * Constructs a new <code>EqualityMatcher</code>.<br/>
     * This matcher does not use the parent matcher to dispatch further calls.
     *
     * @param matcher the parent <code>MatcherInterface</code>
     */
    public EqualityMatcher(MatcherInterface<T> matcher) {
        super(matcher);
        this.didNotMatch = new HashSet<>();
    }

    @Override
    public Matchings<T> match(MergeContext context, T left, T right) {
        long start = System.currentTimeMillis();

        Matchings<T> matchings = new Matchings<>();

        List<Matching<T>> directChildMatchings = new ArrayList<>();
        Iterator<T> lIt = left.getChildren().iterator();
        Iterator<T> rIt = right.getChildren().iterator();

        boolean allMatched = true;

        while (lIt.hasNext() && rIt.hasNext()) {
            T l = lIt.next();
            T r = rIt.next();
            Matchings<T> childMatchings = match(context, l, r);
            Optional<Matching<T>> directChildMatching = childMatchings.get(l, r);

            if (directChildMatching.isPresent()) {
                directChildMatchings.add(directChildMatching.get());
            } else {
                allMatched = false;
            }

            matchings.addAll(childMatchings);
        }

        if (allMatched && left.getNumChildren() == right.getNumChildren() && left.matches(right)) {
            Integer sumScore = directChildMatchings.stream().map(Matching::getScore).collect(SUM_IDENTITY);

            LOG.finer(() -> {
                String format = "%s - Trees are equal: (%s, %s)";
                return String.format(format, ID, left.getId(), right.getId());
            });

            Matching<T> matching = new Matching<>(left, right, sumScore + 1);
            matching.setRuntime(System.currentTimeMillis() - start);
            matchings.add(matching);
        } else {
            didNotMatch.add(UnorderedTuple.of(left, right));

            LOG.finer(() -> {
                String format = "%s - Trees are NOT equal: (%s, %s)";
                return String.format(format, ID, left.getId(), right.getId());
            });
        }

        matchings.stream().forEach(m -> m.setAlgorithm(ID));

        return matchings;
    }

    /**
     * Returns whether this <code>EqualityMatcher</code> determined (in a previous run of
     * {@link #match(MergeContext, Artifact, Artifact)}) that the given pair of <code>Artifact</code>s do not
     * exactly match.
     *
     * @param artifacts
     *         the pair of <code>Artifact</code>s to check
     * @return true if the given <code>Artifact</code>s were checked by the <code>EqualityMatcher</code> and did not
     *          match
     */
    public boolean didNotMatch(UnorderedTuple<T, T> artifacts) {
        return didNotMatch.contains(artifacts);
    }
}
