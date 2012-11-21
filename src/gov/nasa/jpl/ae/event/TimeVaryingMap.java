/**
 * 
 */
package gov.nasa.jpl.ae.event;

import gov.nasa.jpl.ae.solver.HasIdImpl;
import gov.nasa.jpl.ae.solver.IntegerDomain;
import gov.nasa.jpl.ae.solver.StringDomain;
import gov.nasa.jpl.ae.solver.Variable;
import gov.nasa.jpl.ae.util.ClassUtils;
import gov.nasa.jpl.ae.util.CompareUtils;
import gov.nasa.jpl.ae.util.Debug;
import gov.nasa.jpl.ae.util.Pair;
import gov.nasa.jpl.ae.util.Utils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Formatter;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import junit.framework.Assert;

/**
 * 
 * TimeVaryingMap is a {@link TreeMap} for implementing {@link TimeVarying},
 * mapping a {@link Parameter<Integer>} to {@link EffectInstance}s (although the map
 * value has a different encoding, {@link TimeValue}). It is also
 * implements {@link ParameterListener} in order to maintain {@link TreeMap}
 * consistency. It "floats" entries before its {@link Parameter<Integer>} changes to
 * protect the data structure and reinserts the entry after the
 * {@link Parameter<Integer>} has changed.
 * 
 */
public class TimeVaryingMap< T > extends TreeMap< Parameter<Integer>, T >
                                 implements TimeVarying< T >,
                                            Affectable,
                                            ParameterListener,
                                            AspenTimelineWritable {
                                            //Comparable< TimeVaryingMap< T > > {

  private static final long serialVersionUID = -2428504938515591538L;
  
  protected final int id = HasIdImpl.getNext();
  
  /**
   * For the convenience of referring to the effect method.
   */
  protected static Method setValueMethod1 = getSetValueMethod();
  //protected static Method setValueMethod2 = getSetValueMethod2();
  /**
   * Floating effects are those whose time or duration is changing. They must be
   * removed from TimeVaryingMap's map before they change; else, they will
   * corrupt the map. Before changing, they are placed in this floatingEffects
   * list, and after changing they are removed from this list and added back to
   * the map.
   */
  protected List< TimeValue > floatingEffects = new ArrayList< TimeValue >();

  protected String name;
  
  protected Class<?> type = null;

  private static Collection< Method > effectMethods = initEffectMethods();

  public class TimeValue extends Pair< Parameter<Integer>, T >
                               implements HasParameters {

    protected final int id = HasIdImpl.getNext();

    public TimeValue( Parameter<Integer> t, T v ) {
      super( t, v );
    }
    
    @Override
    public boolean isStale() {
      return HasParameters.Helper.isStale( this, false, null, false );
    }

    @Override
    public void setStale( boolean staleness ) {
      Assert.assertTrue( "This method is not supported!", false );
    }

    @Override
    public Set< Parameter< ? > > getParameters( boolean deep,
                                                Set< HasParameters > seen ) {
      Pair< Boolean, Set< HasParameters > > pair = Utils.seen( this, deep, seen );
      if ( pair.first ) return Utils.getEmptySet();
      seen = pair.second;
      //if ( Utils.seen( this, deep, seen ) ) return Utils.getEmptySet();
      return HasParameters.Helper.getParameters( this, deep, null, false );
    }

    @Override
    public Set< Parameter< ? > > getFreeParameters( boolean deep,
                                                    Set< HasParameters > seen ) {
      Assert.assertTrue( "This method is not supported!", false );
      return null;
    }

    @Override
    public void setFreeParameters( Set< Parameter< ? > > freeParams,
                                   boolean deep,
                                   Set< HasParameters > seen ) {
      Assert.assertTrue( "This method is not supported!", false );
    }
    
    @Override
    public boolean isFreeParameter( Parameter< ? > parameter, boolean deep,
                                    Set< HasParameters > seen ) {
      // TODO -- REVIEW -- not sure about this
      Pair< Boolean, Set< HasParameters > > pair = Utils.seen( this, deep, seen );
      if ( pair.first ) return false;
      seen = pair.second;
      //if ( Utils.seen( this, deep, seen ) ) return false;
      return HasParameters.Helper.isFreeParameter( this, parameter, deep, seen, false );
    }

    @Override
    public boolean hasParameter( Parameter< ? > parameter, boolean deep,
                                 Set< HasParameters > seen ) {
      Pair< Boolean, Set< HasParameters > > pair = Utils.seen( this, deep, seen );
      if ( pair.first ) return false;
      seen = pair.second;
      //if ( Utils.seen( this, deep, seen ) ) return false;
      return HasParameters.Helper.hasParameter( this, parameter, deep, seen, false );
    }

    @Override
    public boolean substitute( Parameter< ? > p1, Parameter< ? > p2,
                               boolean deep,
                               Set< HasParameters > seen ) {
      breakpoint();
      Pair< Boolean, Set< HasParameters > > pair = Utils.seen( this, deep, seen );
      if ( pair.first ) return false;
      seen = pair.second;
      //if ( Utils.seen( this, deep, seen ) ) return false;
      return HasParameters.Helper.substitute( this, p1, p2, deep, seen, false );
    }

    @Override
    public int getId() {
      return id;
    }
    @Override
    public int hashCode() {
      return id;
    }

  }

  public static class TimeComparator implements Comparator< Parameter< Integer > > {

    public boolean propagate = false;
    public boolean checkId = true;

    @Override
    public int compare( Parameter< Integer > o1, Parameter< Integer > o2 ) {
      if ( o1 == o2 ) return 0;
      if ( o1 == null ) return -1;
      if ( o2 == null ) return 1;
      if ( ( o1.getType() != null && !Integer.class.isAssignableFrom( o1.getType() ) ) ||
           ( o2.getType() != null && !Integer.class.isAssignableFrom( o2.getType() ) ) ) {
        return o1.compareTo( o2, checkId );
      }
      int compare = 0;
      Integer v1 = null;
      Integer v2 = null;
      try {
        v1 = Expression.evaluate( o1, Integer.class, propagate );
        v2 = Expression.evaluate( o2, Integer.class, propagate );
      } catch (ClassCastException e) {
        // May be possible to get here if a value was not an Integer.
        return o1.compareTo( o2, checkId );
      }
      compare = CompareUtils.compare( v1, v2, true );
      if ( compare != 0 ) return compare;
      return o1.compareTo( o2, checkId );
    }
  }

  /**
   * 
   */
  public TimeVaryingMap( String name ) {
    super(new TimeComparator());
    this.name = name;
  }

  public TimeVaryingMap( String name, Class<?> type ) {
    this(name);
    this.type = type;
  }
  public TimeVaryingMap( String name, T defaultValue, Class<?> type ) {
    super(new TimeComparator());
    this.name = name;
    this.type = type;
    if ( this.type == null && defaultValue != null ) {
      this.type = defaultValue.getClass();
    }
    Parameter<Integer> t = new Parameter<Integer>(null,null, 0, this);
    //System.out.println(name + " put(" + t + ", " + defaultValue + ")" );
    put( t, defaultValue );
    if ( Debug.isOn() ) isConsistent();
  }

  public TimeVaryingMap( String name, T defaultValue ) {
    this(name, defaultValue, null);
  }

  @SuppressWarnings( "unchecked" )
  public TimeVaryingMap( String name, Method initialValueFunction,
                         Object o, int samplePeriod, int horizonDuration ) {
    super(new TimeComparator());
    this.name = name;
    samplePeriod = correctSamplePeriod( samplePeriod, horizonDuration );
    try {
      for ( int t = 0; t < horizonDuration; t += samplePeriod ) {
        // WARNING: creating Parameter<Integer> with no owner in order to avoid
        // unnecessary overhead with constraint processing. If modified while in
        // the map, it can corrupt the map.
        setValue( makeTempTimepoint( t, false ),//new Parameter<Integer>( "", t, this ),
                  (T)initialValueFunction.invoke( o, t ) );
      }
    } catch ( IllegalAccessException e ) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch ( IllegalArgumentException e ) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch ( InvocationTargetException e ) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    if ( Debug.isOn() ) isConsistent();
  }

  protected void breakpoint() {}

  public Parameter<Integer> getTimepointBefore( Parameter<Integer> t ) {
    if ( t == null ) return null;
    return this.lowerKey( t );
  }

  public Parameter<Integer> getTimepointBefore( Integer t ) {
    if ( t == null ) return null;
    Parameter<Integer> tp = makeTempTimepoint( t, false );
    return this.lowerKey( tp );
  }

  public Parameter<Integer> getTimepointEarlier( Parameter<Integer> t ) {
    if ( t == null ) return null;
    return getTimepointEarlier( t.getValue( false ) );
  }

  public Parameter<Integer> getTimepointEarlier( Integer t ) {
    if ( t == null ) return null;
    Integer justBeforeTimeVal = t;
    Parameter<Integer> justBeforeTime = getTimepointBefore( t );
    while ( justBeforeTime != null ) {
      justBeforeTimeVal = justBeforeTime.getValue( false ); 
      if ( justBeforeTimeVal < t ) {
        break;
      }
      justBeforeTime = getTimepointBefore( justBeforeTime );
    }
    return justBeforeTime;
  }

  public Parameter<Integer> getTimepointAfter( Parameter<Integer> t ) {
    if ( t == null ) return null;
    return this.higherKey( t );
  }

  public T getValueBefore( Parameter<Integer> t ) {
    if ( t == null ) return null;
    Parameter<Integer> justBeforeTime = getTimepointBefore( t );
    T valBefore = null;
    if ( justBeforeTime != null ) {
      valBefore = get( justBeforeTime );
    }
    return valBefore;
  }
  
  public T getValueBefore( Integer t ) {
    if ( t == null ) return null;
    Parameter<Integer> justBeforeTime = getTimepointBefore( t );
    T valBefore = null;
    if ( justBeforeTime != null ) {
      valBefore = get( justBeforeTime );
    }
    return valBefore;
  }
  
  public T getValueEarlier( Parameter<Integer> t ) {
    if ( t == null ) return null;
    return getValueEarlier( t.getValue( false ) );
  }
  
  public T getValueEarlier( Integer t ) {
    if ( t == null ) return null;
    Parameter<Integer> justEarlierTime = getTimepointEarlier( t );
    T valBefore = null;
    if ( justEarlierTime != null ) {
      valBefore = get( justEarlierTime );
    }
    return valBefore;
  }


  public Parameter<Integer> makeTempTimepoint( Integer t, boolean maxName ) {
    //if ( t == null ) return null;
    String n = ( maxName ? StringDomain.typeMaxValue : null );
    Parameter<Integer> tp = new Parameter<Integer>( n, null, t, this );
    return tp;
  }
  
  protected static int correctSamplePeriod( int samplePeriod,
                                            int horizonDuration ) {
    if ( samplePeriod == 0 ) {
      samplePeriod = Math.max( 1, horizonDuration / 10 );
    }
    return samplePeriod;
  }
  
  @Override
  public void handleValueChangeEvent( Parameter< ? > parameter ) {
    breakpoint();
    if ( parameter == null ) return;
    if ( parameter instanceof Parameter ) {
      unfloatEffects( (Parameter<Integer>)parameter );
    }
  }

  protected void floatEffects( Parameter<Integer> t ) {
    breakpoint();
    if ( t == null ) return;
    assert t.getValueNoPropagate() != null;
    if ( !containsKey( t ) ) return;
    T value = get( t );
    if ( Debug.isOn() ) Debug.out( getName() + ": floating effect, (" + t + ", " + value + ")" );
    floatingEffects.add( new TimeValue( t, value ) );
//    if ( effects != null ) {
//      if ( effects.second != null ) {
//        for ( Effect e : effects.second ) {
//          EffectInstance r = new EffectInstance( t, 1, e );
//          floatingEffects.add( r );
//        }
//      }
//      // REVIEW -- Should we check to see if the T value is a parameter and do
//      // an owner.handleValueChangeEvent()
//      // if ( effects.first != null ) {
//      // if ( effects.first instanceof ParameterListener ) {
//      // ((ParameterListener)effects.first).handleValueChangeEvent( ? )
//      // Make sure the value of the timeline is stales
//      if ( effects.first != null && effects.first instanceof LazyUpdate ) {
//        ( (LazyUpdate)effects.first ).setStale( true );
//      }
//    }
    remove( t );
    if ( Debug.isOn() ) isConsistent();
  }

  protected void unfloatEffects( Parameter<Integer> t ) {
    breakpoint();
    if ( t == null ) return;
    if ( t.getValueNoPropagate() == null ) return;
    ArrayList<TimeValue> copy = new ArrayList<TimeValue>( floatingEffects );
    for ( TimeValue e : copy ) {
      if ( e.first.compareTo( t ) == 0 ) { // REVIEW -- Do we need to use
                                           // compareTo instead of equals
                                           // elsewhere?
        put( e.first, e.second );
        if ( Debug.isOn() ) Debug.out( getName() + ": unfloated effect, " + e );
      }
      floatingEffects.remove( e );
    }
    if ( Debug.isOn() ) isConsistent();
//    for ( EffectInstance effectInstance : floatingEffects ) {
//      assert effectInstance.startTime != null;
//      if ( effectInstance.startTime.compareTo( t ) == 0 ) {
//        assert effectInstance.effect != null;
//        effectInstance.applyTo( this );
//      }
//    }
//    floatingEffects.remove( t );
  }

  @Override
  public void handleDomainChangeEvent( Parameter< ? > parameter ) {
    if ( parameter instanceof Parameter ) {
      unfloatEffects( (Parameter<Integer>)parameter );
    }
  }

  @Override
  public String getName() {
    if ( name != null && !name.isEmpty() ) return name;
    return getClass().getSimpleName();
  }

  public void setName( String newName ) {
    if ( Utils.isNullOrEmpty( newName ) ) {
      Formatter formatter = new Formatter(Locale.US);
      newName = getClass().getSimpleName();
    }
    this.name = newName;
  }

  // Add startTimes, durations, values that are Parameters, and (if deep)
  // parameters of effects.
  @Override
  public Set< Parameter< ? > > getParameters( boolean deep,
                                              Set< HasParameters > seen ) {
    return Utils.getEmptySet();
    
//    Pair< Boolean, Set< HasParameters > > pair = Utils.seen( this, deep, seen );
//    if ( pair.first ) return Utils.getEmptySet();
//    seen = pair.second;
//    //if ( Utils.seen( this, deep, seen ) ) return Utils.getEmptySet();
//    
//    Set< Parameter< ? > > params = new TreeSet< Parameter< ? > >();
//    params = Utils.addAll( params, HasParameters.Helper.getParameters( keySet(), deep, seen ) );
//    params = Utils.addAll( params, HasParameters.Helper.getParameters( values(), deep, seen ) ); 
//    params = Utils.addAll( params, HasParameters.Helper.getParameters( floatingEffects, deep, seen ) );
//    return params;
  }

  public Set< Parameter< ? > > getParameters() {
    HashSet< HasParameters > seen = new HashSet< HasParameters >();
    Set< Parameter< ? > > params = new HashSet< Parameter< ? > >();
    params = Utils.addAll( params, HasParameters.Helper.getParameters( keySet(), false, seen, true ) );
    params = Utils.addAll( params, HasParameters.Helper.getParameters( values(), false, seen, true ) ); 
    params = Utils.addAll( params, HasParameters.Helper.getParameters( floatingEffects, false, seen, true ) );
    return params;
  }

  @Override
  public Set< Parameter< ? > > getFreeParameters( boolean deep,
                                                  Set< HasParameters > seen ) {
    Assert.assertTrue( "This method is not supported!", false );
    return null;
  }
  @Override
  public void setFreeParameters( Set< Parameter< ? > > freeParams,
                                 boolean deep,
                                 Set< HasParameters > seen) {
    Assert.assertTrue( "This method is not supported!", false );
  }
  

  @Override
  public boolean hasParameter( Parameter< ? > parameter, boolean deep,
                               Set< HasParameters > seen ) {
    Pair< Boolean, Set< HasParameters > > pair = Utils.seen( this, deep, seen );
    if ( pair.first ) return false;
    seen = pair.second;

    if ( HasParameters.Helper.hasParameter( this, parameter, deep, seen, false ) ) {
      return true;
    }
    if ( HasParameters.Helper.hasParameter( floatingEffects, parameter, deep, seen, true ) ) {
      return true;
    }
    return false;
  }

  @Override
  public boolean
      substitute( Parameter< ? > p1, Parameter< ? > p2, boolean deep,
                  Set< HasParameters > seen ) {
    breakpoint();
    if ( HasParameters.Helper.substitute( this, p1, p2, deep, seen, false ) ) {
      if ( Debug.isOn() ) isConsistent();
      return true;
    }
    if ( HasParameters.Helper.substitute( floatingEffects, p1, p2, deep, seen, true ) ) {
      if ( Debug.isOn() ) isConsistent();
      return true;
    }
    return false;
    
  }

  /* (non-Javadoc)
   * @see gov.nasa.jpl.ae.event.LazyUpdate#isStale()
   */
  @Override
  public boolean isStale() {
    if ( !floatingEffects.isEmpty() ) return true;
    return ( HasParameters.Helper.isStale( this, false, null, false ) );
  }

  /* (non-Javadoc)
   * @see gov.nasa.jpl.ae.event.LazyUpdate#setStale(boolean)
   */
  @Override
  public void setStale( boolean staleness ) {
    if ( Debug.isOn() ) Debug.outln( "setStale(" + staleness + ") to " + this );
    Assert.assertTrue( "This method is not supported!", false );
  }

  /* (non-Javadoc)
   * @see gov.nasa.jpl.ae.event.ParameterListener#setStaleAnyReferencesTo(gov.nasa.jpl.ae.event.Parameter)
   */
  @Override
  public void setStaleAnyReferencesTo( Parameter< ? > changedParameter ) {
    breakpoint();
    if ( Debug.isOn() ) Debug.outln( getName() + ".setStaleAnyReferencesTo(" + changedParameter + ")" );
    if ( changedParameter == null ) return;
    if ( containsKey( changedParameter ) ) {
      if ( Debug.isOn() ) Debug.outln( getName() + ".setStaleAnyReferencesTo(" + changedParameter + "): does contain" );
      floatEffects( (Parameter<Integer>)changedParameter );
    } else {
      if ( Debug.isOn() ) Debug.outln( getName() + ".setStaleAnyReferencesTo(" + changedParameter + "): does not contain" );
    }
    if ( Debug.isOn() ) isConsistent();
    // TODO -- REVIEW -- should we float EffectInstances that have the parameter
    // and set a stale flag in EffeectInstances?
  }

  /* (non-Javadoc)
   * @see gov.nasa.jpl.ae.event.ParameterListener#refresh(gov.nasa.jpl.ae.event.Parameter)
   */
  @Override
  public boolean refresh( Parameter< ? > parameter ) {
    // TODO -- REVIEW -- do nothing? owner's responsibility?
    // TODO -- TimeVaryingMap should own the values if they are Parameters and
    // maybe any Parameters the value has itself, unless the value is a
    // ParameterListener and owns its own Parameters.
    return false;
  }

  /* (non-Javadoc)
   * @see gov.nasa.jpl.ae.event.TimeVarying#getValue(gov.nasa.jpl.ae.event.Parameter<Integer>)
   */
  @Override
  public T getValue( Parameter< Integer > t ) {
    if ( t == null ) return null;
    T v = get( t ); //.first;
    if ( Debug.isOn() ) isConsistent();
    if ( v != null ) return v;
    // Saving this check until later in case a null time value is acceptable,
    // and get(t) above works.
    if ( t.getValue( false ) == null ) return null;
    return getValue( t.getValue( false ) );
  }

  /* (non-Javadoc)
   * @see gov.nasa.jpl.ae.event.TimeVarying#getValue(java.lang.Integer)
   */
  @Override
  public T getValue( Integer t ) {
    if ( t == null ) return null;
    Parameter<Integer> tp = makeTempTimepoint( t, true );
    Entry< Parameter<Integer>, T > e = this.floorEntry( tp );
    if ( Debug.isOn() ) isConsistent();
    if ( e != null ) return e.getValue();
//  if ( !isEmpty() && firstEntry().getKey().getValue() <= t ) {
//    return firstEntry().getValue();
//  }
    if ( !isEmpty() ) {
      Entry< Parameter<Integer>, T > f = firstEntry(); 
      Parameter<Integer> k = f.getKey();
      if ( IntegerDomain.defaultDomain.lessEquals( k.getValue( false ), t ) ) {
        return f.getValue();
      }
    }
    return null;
  }

  /**
   * Return the Parameter<Integer>1 for an entry (Parameter<Integer>1, value1) such that
   * Parameter<Integer>1.value equals tp.value, and value1 equals value.
   * 
   * @param value
   *          the value to match with a value in the map
   * @param tp
   *          the time value required for the returned key
   * @return a Parameter<Integer> key in the map, whose time value equals tp's value and
   *         whose map entry equals value or null if there is no such Parameter<Integer>.
   */
  public boolean hasValueAt( T value, Parameter<Integer> tp ) {
    if ( tp == null ) return false;
    T v = get( tp ); //.first;
    if ( value == v ) return true;
    if ( v != null ) return value.equals( v );
    // Saving this check until later in case a null time value is acceptable,
    // and get(t) above works.
    if ( tp.getValue( false ) == null ) return false;
    return hasValueAt( value, tp.getValueNoPropagate() );
  }
  
  /**
   * Return the Parameter<Integer>1 for an entry (Parameter<Integer>1, value1) such that
   * Parameter<Integer>1.value equals t, and value1 equals value.
   * 
   * @param value
   *          the value to match with a value in the map
   * @param t
   *          the time value required for the returned key
   * @return a Parameter<Integer> key in the map, whose time value equals t and whose map
   *         entry equals value or null if there is no such Parameter<Integer>.
   */
  public Parameter<Integer> keyForValueAt( T value, Integer t ) {
    if ( t == null ) return null;
    Parameter<Integer> tp = makeTempTimepoint( t, false );//new Parameter<Integer>( null, t, null );
    Entry< Parameter<Integer>, T > e = this.floorEntry( tp );
    Parameter<Integer> startKey = null;
    if ( e != null ) {
      startKey = e.getKey();
    } else if ( !isEmpty() ) {
      startKey = firstEntry().getKey();
    } else {
      return null;
    }
    NavigableMap< Parameter<Integer>, T > tailMap = this.tailMap( startKey, true );
    for ( java.util.Map.Entry< Parameter<Integer>, T > te : tailMap.entrySet() ) {
      Object mVal = te.getValue();
      if ( Utils.valuesEqual( value, mVal ) &&
          TimeDomain.defaultDomain.equals( t, te.getKey().getValueNoPropagate() ) ) {
        return te.getKey();
      }
      if ( TimeDomain.defaultDomain.greater( te.getKey().getValueNoPropagate(), t ) ) break;
    }
//    } else if ( !isEmpty() ) {
//      Object mVal = firstEntry().getValue();
//      if ( ( ( value == null && mVal == null ) || ( value != null && value.equals( mVal ) ) ) &&
//          t.equals( firstEntry().getKey().getValueNoPropagate() ) ) {
//        return firstEntry().getKey();
//      }
//    }
    return null;
  }
  
  /**
   * @param value
   * @param t
   * @return
   */
  public boolean hasValueAt( T value, Integer t ) {
    return keyForValueAt( value, t ) != null;
  }

//  /**
//   * Inserts the value in the map if there is not 
//   * @param t
//   * @param value
//   * @return
//   */
//  public T setValue( Integer t, T value ) {
//    breakpoint();
//    if ( t == null ) {
//      if ( Debug.isOn() ) Debug.error( true, "Error! trying to insert a null Parameter<Integer> into the map" );
//      return null;
//    }
//    T oldValue = null;
//    Parameter<Integer> tp = keyForValueAt( value, t );
//    if ( Debug.isOn() ) isConsistent();
//    if ( tp == null ) {
//      tp = makeTempParameter<Integer>( t, false );//new Parameter<Integer>( "", t, null );
//      oldValue = put( tp, value );
//      if ( Debug.isOn() ) isConsistent();
//    }
//    return oldValue;
//  }

  /* (non-Javadoc)
   * @see gov.nasa.jpl.ae.event.TimeVarying#setValue(gov.nasa.jpl.ae.event.Parameter<Integer>, java.lang.Object)
   */
  @Override
  public T setValue( Parameter< Integer > t, T value ) {
    breakpoint();
    if ( Debug.isOn() ) Debug.outln( getName() + "setValue(" + t + ", " + value + ")" );
    if ( t == null ) {
      if ( Debug.isOn() ) Debug.error( false, "Error! trying to insert a null Parameter<Integer> into the map!" );
      return null;
    }
    if ( t.getValueNoPropagate() == null ) {
      if ( Debug.isOn() ) Debug.error( false, "Error! trying to insert a null Parameter<Integer> value into the map!" );
      return null;
    }
    if ( t.getOwner() == null ) {
      if ( Debug.isOn() ) Debug.error( false, "Warning: inserting a Parameter<Integer> with null owner into the map--may be detached!" );
    }
    if ( value != null && value instanceof Parameter && ((Parameter)value).getOwner() == null ) {
      if ( Debug.isOn() ) Debug.error( true, "Warning: trying to insert a value with a null owner into the map--may be detached!" );
    }
    T oldValue = null;
    Parameter<Integer> tp = keyForValueAt( value, t.getValue( false ) );
    if ( Debug.isOn() ) isConsistent();
    if ( tp != null && tp != t ) {
      remove( tp );
    }
    if ( tp != t ) {
      oldValue = put( t, value );
      if ( value != null &&
           ( type == null || value.getClass().isAssignableFrom( type ) ) ) {
        type = value.getClass();
      }
    }
    if ( Debug.isOn() ) isConsistent();
    if ( Debug.isOn() ) Debug.outln( getName() + "setValue(" + t + ", " + value
                                     + ") returning oldValue=" + oldValue );
    return oldValue;
  }

  public boolean isConsistent2() {
    return true;
  }
  /**
   * Validate the consistency of the map for individual and adjacent entries.
   * @return whether or not the entries in the map make sense.
   */
  public boolean isConsistent() {    
    Parameter<Integer> lastTp = null;
    int lastTime = -1;
    T lastValue = null;
    boolean ok = true;
    ArrayList<T> valuesAtSameTime = new ArrayList< T >();
    boolean firstEntry = true;
    for ( Map.Entry< Parameter<Integer>, T >  entry : entrySet() ) {
      Parameter<Integer> tp = entry.getKey();
      T value = null;
      try {
        value = entry.getValue();
      } catch ( ClassCastException cce ) {
        ok = false;
        System.err.println( "Error! Value " + entry.getValue() 
                            + " has the wrong type in TimeVaryingMap! "
                            + this );
        cce.printStackTrace();
      }
      int time = -1;
      boolean TimepointValueChanged = true;
      boolean valueChanged = true;
      boolean duplicateValueAtTime = false;
      // Check for problems with the key.
      // No null keys.
      if ( tp == null ) {
        if ( Debug.isOn() ) {
          Debug.error( true, "Error! null key in TimeVaryingMap " + getName() );
        }
        ok = false;
      } else if ( tp.getValueNoPropagate() == null ) {
        // No null values for Parameter<Integer> key.  
        if ( Debug.isOn() ) Debug.errorOnNull( true, "Error! null Parameter<Integer> value in TimeVaryingMap "
                                 + getName(), tp.getValueNoPropagate() );
        ok = false;
      } else {
       TimepointValueChanged = !tp.getValueNoPropagate().equals( lastTime );
        if ( !firstEntry && tp.getValueNoPropagate() < lastTime ) {
          // Time cannot decrease.
          if ( Debug.isOn() ) Debug.error( true, "Error! time value for entry " + entry
                             + " should be >= " + lastTime
                             + " in TimeVaryingMap " + getName() );
          ok = false;
        } else {
          lastTime = tp.getValueNoPropagate();
        }
      }
      if ( TimepointValueChanged ) {
        valuesAtSameTime.clear();
      }
      duplicateValueAtTime = valuesAtSameTime.contains( value );
      valueChanged = value != lastValue; 
      if ( tp != null && tp == lastTp ) {
        // A key should have only one entry.
        if ( Debug.isOn() ) Debug.error( true, "Error! Parameter<Integer> has duplicate entry " + entry
                     + " in TimeVaryingMap " + getName() );
        ok = false;
      }
      
      // Check for problems with the value.
      if ( !firstEntry && duplicateValueAtTime ) {
        if ( Debug.isOn() ) Debug.error( true, "Error! duplicate entry of value " + value
                     + " at time " + tp + " with value set "
                     + valuesAtSameTime + " for TimeVaryingMap "
                     + getName() );
        ok = false;
//      } else if ( !firstEntry && !valueChanged ) {
//        if ( Debug.isOn() ) Debug.error( false, "Warning! value " + value
//                           + " repeated for adjacent entry " + entry + " at time "
//                           + tp + " for TimeVaryingMap " + this );
      }
      lastTp = tp;
      lastValue = value;
      if ( !duplicateValueAtTime ) {
        valuesAtSameTime.add( value );
      }
      firstEntry = false;
    }
    if ( ok ) {
      if ( Debug.isOn() ) Debug.outln( getName() + " is consistent" );
    } else {
      if ( Debug.isOn() ) Debug.outln( getName() + " is not consistent: " + this );
    }
    return ok;
  }
  
  @Override
  public T unsetValue( Parameter< Integer > t, T value ) {
    breakpoint();
    if ( t == null ) {
      if ( Debug.isOn() ) Debug.error( false, "Warning! unsetValue(" + t + ", " + value
                          + "): Null Parameter<Integer> key for TimeVaryingMap " + this );
      return null;
    }
    if ( t.getValueNoPropagate() == null ) {
      if ( Debug.isOn() ) Debug.error( false,
                   "Warning! unsetValue(" + t + ", " + value
                       + "): Null value for Parameter<Integer> key for TimeVaryingMap "
                       + this );
      return null;
    }
    T oldValue = null;
    Parameter<Integer> tpInMap = keyForValueAt( value, t.getValueNoPropagate() );
    if ( tpInMap != t ) {
      if ( Debug.isOn() ) Debug.error( false, "Warning! unsetValue(" + t + ", " + value 
                          + "): Parameter<Integer> key is not in the map for TimeVaryingMap "
                          + this );
    }
    if ( tpInMap == null ) {
      if ( Debug.isOn() ) Debug.error( false, "Warning! unsetValue(" + t + ", " + value
                          + "): no matching entry in TimeVaryingMap " + this );
    } else {
      oldValue = get( tpInMap );
      if ( Debug.isOn() ) Debug.outln( "unsetValue(" + t + ", " + value + "): removing entry ("
                   + tpInMap + ", " + oldValue + ") in " + this );
      remove( tpInMap );
    }
    if ( Debug.isOn() ) isConsistent();
    return oldValue;
  }

  public void unapply( Effect effect ) {
    Pair< Parameter<Integer>, T > p = 
        getTimepointAndValueOfEffect( effect,
                                      getSetValueMethod(),
                                      getSetValueMethod() );
    if ( p != null ) {
      unsetValue( p.first, p.second );
    }
  }
  
  @Override
  public void detach( Parameter< ? > parameter ) {
    if ( parameter instanceof Parameter ) {
      remove( (Parameter<Integer>)parameter );
    }
    for ( Entry< Parameter<Integer>, T > e : ( (TimeVaryingMap< T >)this.clone() ).entrySet() ) {
      if ( e.getValue() instanceof HasParameters ) {
        if ( ( (HasParameters)e.getValue() ).hasParameter( parameter, false,
                                                           null ) ) {
          detach( e.getKey() );
        }
      }
    }
  }


  @Override
  public boolean isFreeParameter( Parameter< ? > parameter, boolean deep,
                                  Set< HasParameters > seen ) {
    Pair< Boolean, Set< HasParameters > > pair = Utils.seen( this, deep, seen );
    if ( pair.first ) return false;
    seen = pair.second;
    //if ( Utils.seen( this, deep, seen ) ) return false;
    return HasParameters.Helper.isFreeParameter( this, parameter, deep, seen, false );
  }

  @Override
  public boolean equals( Object o ) {
    if ( this == o ) return true;
    if ( o instanceof TimeVarying ) {
      return ( compareTo( (TimeVarying< T >)o ) == 0 );
    }
    return false;
  }
  
  /* (non-Javadoc)
   * @see java.lang.Comparable#compareTo(java.lang.Object)
   */
  @Override
  public int compareTo( TimeVarying< T > o ) {
    return compareTo( o, true );
  }
  public int compareTo( TimeVarying< T > o, boolean checkId ) {
    if ( o == null ) return 1;
    if ( checkId ) return CompareUtils.compare( getId(), o.getId() );
    int compare = 0;
    if ( o instanceof TimeVaryingMap ) {
      TimeVaryingMap<?> otvm = (TimeVaryingMap)o;
      compare = CompareUtils.compare( getName(), otvm.getName(), true );
      if ( compare != 0 ) return compare;
    }
    Debug.err( "TimeVaryingMap.compareTo() may compare values, which, if changed while this is in a map, can corrupt the map." );
    compare = CompareUtils.compare( this, o ); // WARNING: values change!!!
    if ( compare != 0 ) return compare;
    return compare;
  }
  
  public static Method getSetValueMethod() {
    return getSetValueWithParameterMethod();
  }
  public static Method getSetValueWithParameterMethod() {
    if ( setValueMethod1 == null ) {
      for ( Method m : TimeVaryingMap.class.getMethods() ) {
        if ( m.getName().equals("setValue") && m.getParameterTypes() != null
             && m.getParameterTypes().length == 2 
             && m.getParameterTypes() [0] == Parameter.class ) {
          setValueMethod1 = m;
        }
      }
    }
    return setValueMethod1;
  }
  
  public <TT> Pair< Parameter<Integer>, TT > getTimepointAndValueOfEffect( Effect effect,
                                                                  Method method1,
                                                                  Method method2 ) {
    Pair< Object, TT > p1 = getTimeAndValueOfEffect( effect, method1, method2 );
    if ( p1 == null ) return null;
    Object t = p1.first;//effectFunction.arguments.get( 0 );
    TT value = p1.second;
    Parameter<Integer> tp = null;
    if ( t instanceof Integer ) {
      tp = makeTempTimepoint( (Integer)t, true );
    } else if ( t instanceof Parameter ) {
      tp = (Parameter<Integer>)t;
    } else {
      System.err.println( "Effect(" + effect + ") has wrong arguments to "
                          + method1.getName() + " for " + this );
      return null;
    }
    Pair< Parameter<Integer>, TT > p2 = new Pair( tp, value );
    return p2;
  }
  public <TT> Pair< Object, TT > getTimeAndValueOfEffect( Effect effect,
                                                          Method method1,
                                                          Method method2 ) {
    if ( !( effect instanceof EffectFunction ) ) {
      return null;
    }
    EffectFunction effectFunction = (EffectFunction)effect;
    if ( effectFunction == null || effectFunction.getMethod() == null ) {
      if ( Debug.isOn() ) Debug.errln( getName() + ".getTimeAndValueOfEffect(Effect="
                   + effect + ", Method=" + method1 + ", Method=" + method2
                   + ") called with no effect method! " + this );
      return null;
    }
    boolean isMethod1 = effectFunction.getMethod().equals(method1);
    boolean isMethod2 =  effectFunction.getMethod().equals( method2);
    if ( isMethod1  || isMethod2 ) {
      if ( effectFunction.arguments != null && effectFunction.arguments.size() >= 2 ) {
        Object t = effectFunction.arguments.get( 0 );
        Object o = effectFunction.arguments.get( 1 );
        TT value = null;
        try {
          value = (TT)o;
        } catch( Exception e ) {
          //e.printStackTrace();
        }
        return new Pair< Object, TT >( t, value ); 
      }
    }
    return null;
  }
  
  @Override
  public boolean isApplied( Effect effect ) {
    return isApplied(effect, getSetValueMethod(), getSetValueMethod()//getSetValueMethod2()
                     );
  }
  // FIXME -- TODO -- should check to see if value matches!!!
  public boolean isApplied( Effect effect, Method method1, Method method2 ) {
    breakpoint();
    if ( Debug.isOn() ) isConsistent();
    Pair< Object, T > p = getTimeAndValueOfEffect( effect, method1, method2 );
    if ( p == null ) return false;
    Object t = p.first;
    T value = p.second;
    if ( value != null ) {
      if ( t instanceof Parameter ) {
        return hasValueAt( value, (Parameter<Integer>)t );
      } if ( t instanceof Integer ) {
        return hasValueAt( value, (Integer)t );
      }
    }
    return false;
  }
  
  @Override
  public String toString() {
    StringBuffer sb = new StringBuffer();
    sb.append( this.getName() );
    sb.append( super.toString() );
    return sb.toString();
  }

  @Override
  public < T > boolean pickValue( Variable< T > variable ) {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public int getId() {
    return id;
  }
  @Override
  public int hashCode() {
    return id;
  }

  public Class<?> getType() {
    if ( type != null ) return type;
    for ( T t : values() ) {
      if ( t != null ) return t.getClass();
    }
    return null;
  }
  
  public Number getMinValue() {
    return getMinOrMaxValue( true );
  }
  public Number getMaxValue() {
    return getMinOrMaxValue( false );
  }
  public Number getMinOrMaxValue( boolean isMin ) {
    if ( !ClassUtils.isNumber( getType() ) ) return null;
    boolean isInt = ClassUtils.isInteger( getType() );
    Integer minInt = Integer.MAX_VALUE;
    Double minDouble = Double.MAX_VALUE;
    int mul = isMin ? 1 : -1;
    for ( T v : values() ) {
      if ( v != null &&  v instanceof Number ) {
        if ( isInt ) {
          Integer i = ((Number)v).intValue();
          if ( mul * i.intValue() < mul * minInt.intValue() ) {
            minInt = i;
          }
        } else {
          Double d = ((Number)v).doubleValue();
          if ( mul * d.doubleValue() < mul * minDouble.doubleValue() ) {
            minDouble = d;
          }
        }
      }
    }
    return isInt ? minInt : minDouble;
  }

  @Override
  public String toAspenMdl( String tlName ) {
    if ( Utils.isNullOrEmpty( tlName ) ) {
      tlName = getName();
    }
    StringBuffer sb = new StringBuffer();
    if ( ClassUtils.isNumber( getType() ) ) {
      boolean isInt = ClassUtils.isInteger( getType() );
      Number minVal = getMinValue();
      Number maxVal = getMaxValue();
      // If all values are positive, set minVal to 0 unless it would make it
      // hard to see the difference.
      if ( minVal.doubleValue() > 0.0 && maxVal.doubleValue() / minVal.doubleValue() > 1.2 ) {
        minVal = 0;
      }
      sb.append( "resource " + tlName + " {\n" );
      sb.append( "  type = depletable;\n" );
      sb.append( "  min_value = " + minVal.doubleValue() + ";\n" );
      sb.append( "  capacity = " + maxVal.doubleValue() + ";\n" );
      sb.append( "  default = " + minVal.doubleValue() + ";\n" );
      sb.append( "}\n" );
      
      sb.append( "activity " + tlName + "Changer {\n" );
      sb.append( "  duration = 1;\n" );
      //sb.append( "  " + (isInt?"int":"real") + " value;\n");
      sb.append( "  real value;\n");
      sb.append( "  reservations = " + tlName + " use value;\n");
      sb.append( "}\n" );
    } else {
      sb.append( "state_variable " + tlName + " {\n  default = \"\";\n}\n" );
      sb.append( "activity " + tlName + "Changer {\n" );
      sb.append( "  duration = 1;\n" );
      sb.append( "  string value;\n" );
      sb.append( "  reservations = " + tlName + " change_to value at_start;\n");
      sb.append("}\n" );
    }
    return sb.toString();
  }

  @Override
  public String toAspenIni( String tlName ) {
    StringBuffer sb = new StringBuffer();
    boolean isNum = ClassUtils.isNumber( getType() );
    boolean isInt = isNum && ClassUtils.isInteger( getType() );
    int ctr = 0;
    for ( Map.Entry< Parameter< Integer >, T > e : entrySet() ) {
      if ( e.getKey() == null || e.getKey().getValue(false) == null ) continue;
      int startTime = e.getKey().getValue(false).intValue();
      String q = isNum ? "" : "\"";
      Object value = e.getValue();
      if ( isNum && value == null ) {
        value = new Double(getMinValue().doubleValue());
      }
      if ( isNum && value != null ) {
        Number d = (Number)value;
        value = d.doubleValue();
      }
      value = q + value + q;
      sb.append( tlName + "Changer " + tlName + "Changer_"  + (ctr++) + " {\n" );
      sb.append( "  start_time = " + Math.max( 0, Math.min( 1073741823, startTime)) + ";\n" );
      sb.append( "  value = " + value + ";\n" );
      sb.append("}\n" );
    }
    sb.append("\n");
    return sb.toString();
  }

  @Override
  public Collection< Method > getEffectMethods() {
    if ( effectMethods == null ) effectMethods = initEffectMethods();
    return effectMethods;
  }

  @Override
  public boolean doesAffect( Method method ) {
    return getEffectMethods().contains( method );
  }

  protected static Collection< Method > initEffectMethods() {
    effectMethods = new HashSet<Method>();
    Method m = getSetValueMethod();
    if ( m != null ) effectMethods.add( m );
    //m = getSetValueMethod2();
    //m = TimeVaryingMap.class.getMethod("unsetValue");
    //m = TimeVaryingMap.class.getMethod("unapply");
    return effectMethods;
  }

}
