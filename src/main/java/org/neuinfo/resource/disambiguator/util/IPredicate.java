package org.neuinfo.resource.disambiguator.util;

/**
 * Created by bozyurt on 3/20/14.
 */
public  interface  IPredicate<T> {

    public boolean satisfied(T obj);
}
