package org.eclipse.jetty.util;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

public class IncludeExclude<ITEM> {
    private final Set<ITEM> _includes;
    private final Predicate<ITEM> _includePredicate;
    private final Set<ITEM> _excludes;
    private final Predicate<ITEM> _excludePredicate;
    
	private static class SetContainsPredicate<ITEM> implements Predicate<ITEM> {
        private final Set<ITEM> set;

		public SetContainsPredicate(Set<ITEM> set) {
            this.set = set;
        }
        @Override
		public boolean test(ITEM item) {
            return set.contains(item);
        }
    }

	@SuppressWarnings("unchecked")
	public IncludeExclude()
    {
        this(HashSet.class);
    }
    
    /**
     * Construct an IncludeExclude.
     * <p>
     * If the {@link Set} class also implements {@link Predicate}, then that Predicate is
     * used to match against the set, otherwise a simple {@link Set#contains(Object)} test is used.
     * @param setClass The type of {@link Set} to using internally
     * @param <SET> the {@link Set} type
     */
	@SuppressWarnings("unchecked")
	public <SET extends Set<ITEM>> IncludeExclude(Class<SET> setClass)
    {
        try
        {
            _includes = setClass.newInstance();
            _excludes = setClass.newInstance();
            
            if(_includes instanceof Predicate) {
                _includePredicate = (Predicate<ITEM>)_includes;
            } else {
                _includePredicate = new SetContainsPredicate<>(_includes);
            }
            
            if(_excludes instanceof Predicate) {
                _excludePredicate = (Predicate<ITEM>)_excludes;
            } else {
                _excludePredicate = new SetContainsPredicate<>(_excludes);
            }
        }
        catch (InstantiationException | IllegalAccessException e)
        {
            throw new RuntimeException(e);
        }
    }
    
    /**
     * Construct an IncludeExclude
     * 
     * @param includeSet the Set of items that represent the included space 
     * @param includePredicate the Predicate for included item testing
     * @param excludeSet the Set of items that represent the excluded space
     * @param excludePredicate the Predicate for excluded item testing
     * @param <SET> the {@link Set} type
     */
    public <SET extends Set<ITEM>> IncludeExclude(Set<ITEM> includeSet, Predicate<ITEM> includePredicate, Set<ITEM> excludeSet, Predicate<ITEM> excludePredicate)
    {
        Objects.requireNonNull(includeSet,"Include Set");
        Objects.requireNonNull(includePredicate,"Include Predicate");
        Objects.requireNonNull(excludeSet,"Exclude Set");
        Objects.requireNonNull(excludePredicate,"Exclude Predicate");
        
        _includes = includeSet;
        _includePredicate = includePredicate;
        _excludes = excludeSet;
        _excludePredicate = excludePredicate;
    }
    
    public void include(ITEM element)
    {
        _includes.add(element);
    }
    
	@SuppressWarnings("unchecked")
	public void include(ITEM... element) {
		for (ITEM e : element) {
			_includes.add(e);
		}
    }

	public void exclude(ITEM element) {
        _excludes.add(element);
    }
    
	@SuppressWarnings("unchecked")
	public void exclude(ITEM... element) {
		for (ITEM e : element) {
			_excludes.add(e);
		}
    }
	public boolean matches(ITEM e) {
		if (!_includes.isEmpty() && !_includePredicate.test(e)) {
			return false;
		}
        return !_excludePredicate.test(e);
    }
	public int size() {
        return _includes.size()+_excludes.size();
    }
	public Set<ITEM> getIncluded() {
        return _includes;
	}
	public Set<ITEM> getExcluded() {
        return _excludes;
    }
	public void clear() {
        _includes.clear();
        _excludes.clear();
    }
    @Override
	public String toString() {
        return String.format("%s@%x{i=%s,ip=%s,e=%s,ep=%s}",this.getClass().getSimpleName(),hashCode(),_includes,_includePredicate,_excludes,_excludePredicate);
    }
}
