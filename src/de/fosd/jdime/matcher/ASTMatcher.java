/**
 * 
 */
package de.fosd.jdime.matcher;

import de.fosd.jdime.common.Artifact;

/**
 * @author Olaf Lessenich
 * 
 * @param <T> type of artifact
 */
public class ASTMatcher<T extends Artifact<T>> implements MatchingInterface<T> {

	
	/**
	 * 
	 */
	private int calls = 0;
	
	/**
	 * 
	 */
	private UnorderedMatcher<T> unorderedMatcher;
	
	/**
	 * 
	 */
	private OrderedMatcher<T> orderedMatcher;
	
	/**
	 * 
	 */
	public ASTMatcher() {
		unorderedMatcher = new UnorderedMatcher<T>(this);
	}
	
	/**
	 * Logger.
	 */
	//private static final Logger LOG = Logger.getLogger(ASTMatcher.class);
	
	/**
	 * @param left artifact
	 * @param right artifact
	 * @return Matching
	 */
	public final Matching<T> match(final T left, final T right) {
		boolean isOrdered = false;

		for (int i = 0; !isOrdered && i < left.getNumChildren(); i++) {
			if (left.getChild(i).isOrdered()) {
				isOrdered = true;
			}
		}

		for (int i = 0; !isOrdered && i < right.getNumChildren(); i++) {
			if (right.getChild(i).isOrdered()) {
				isOrdered = true;
			}
		}
		
		calls++;

		return isOrdered ? orderedMatcher.match(left, right)
				: unorderedMatcher.match(left, right);
	}

	/**
	 * Marks corresponding nodes using an already computed matching. The
	 * respective nodes are flagged with <code>matchingFlag</code> and
	 * references are set to each other.
	 * 
	 * @param matching
	 *            used to mark nodes
	 * @param color
	 *            color used to highlight the matching in debug output
	 */
	public final void storeMatching(final Matching<T> matching, 
			final Color color) {
		T left = matching.getLeft();
		T right = matching.getRight();

		assert (left.matches(right));

		if (matching.getScore() > 0) {
			matching.setColor(color);
			left.addMatching(matching);
			right.addMatching(matching);
		}

		for (Matching<T> childMatching : matching.getChildren()) {
			storeMatching(childMatching, color);
		}
	}
	
	/**
	 * Resets the call counter.
	 */
	public final void reset() {
		calls = 0;
		orderedMatcher = new OrderedMatcher<T>(this);
		unorderedMatcher = new UnorderedMatcher<T>(this);
	}
	
	/**
	 * Returns the logged call counts.
	 * @return logged call counts
	 */
	public final String getLog() {
		StringBuffer sb = new StringBuffer();
		sb.append("matcher calls (all/ordered/unordered): ");
		sb.append(calls + "/");
		sb.append(orderedMatcher.getCalls() + "/");
		sb.append(unorderedMatcher.getCalls());
		assert (calls == unorderedMatcher.getCalls() 
				+ orderedMatcher.getCalls()) 
				: "Wrong sum for matcher calls";
		return sb.toString();
	}
}
