/**
 * 
 */
package gov.nasa.jpl.ae.event;

import java.util.Collections;

/**
 * A time-varying map of time-varying maps, which are plottable and projected.
 * 
 *
 */
public class TimeVaryingProjection< V > extends
                                        TimeVaryingPlottableMap< TimeVaryingPlottableMap< V > > {

  /**
   * 
   */
  private static final long serialVersionUID = -7124939704592838080L;

//  /**
//   * @param name
//   * @param fileName
//   * @param defaultValue
//   * @param cls
//   */
//  public TimeVaryingProjection( String name, String fileName,
//                                TimeVarying< T > defaultValue,
//                                Class< TimeVarying< T > > cls ) {
//    super( name, fileName, defaultValue, cls, true );
//  }
//
  /**
   * @param name
   * @param fileName
   * @param defaultValue
   * @param cls
   */
  public TimeVaryingProjection( String name, String fileName,
                                V defaultValue,
                                Class< V > cls ) {
    super( name, (String)null,
           new TimeVaryingPlottableMap< V >( name, fileName, defaultValue,
                                             cls, true ),
           null, true );
  }

  /**
   * @param name
   */
  public TimeVaryingProjection( String name ) {
    super( name );
    this.dataProjected = true;
  }

  /**
   * @param name
   * @param fileName
   */
  public TimeVaryingProjection( String name, String fileName ) {
    super( name, fileName );
    this.dataProjected = true;
  }

  public void setValue( Timepoint t1, Timepoint t2, V value ) {
    
    TimeVaryingPlottableMap< V > newMap = getValue( t1 );
    if ( newMap == null ) {
      Class<?> cls = ( value == null ? null : value.getClass() );
      newMap = new TimeVaryingPlottableMap< V >( name, null, null, null, true );
    }
    newMap.setValue( t2, value );
    super.setValue( t1, newMap );
  }
  
  public V getValue( Timepoint t1, Timepoint t2 ) {
    
    TimeVaryingPlottableMap< V > newMap = getValue( t1 );
    if ( newMap == null ) {
      return null;
    }
    return newMap.getValue( t2 );
  }
  
  /**
   * @param args
   */
  public static void main( String[] args ) {
    TimeVaryingProjection< Double > tvp =
        new TimeVaryingProjection< Double >( "tvm", null, 2.0, Double.class );

    Timepoint t0 = new Timepoint(0);
    Timepoint t2 = new Timepoint(2);
    Timepoint t3 = new Timepoint(3);
    Timepoint t4 = new Timepoint(4);
    Timepoint t6 = new Timepoint(6);
    tvp.setValue( t0, t0, 0.0 );
    tvp.setValue( t0, t2, 4.0 );
    tvp.setValue( t0, t4, 16.0 );
    tvp.setValue( t2, t4, 8.0 );
    tvp.setValue( t2, t6, 12.0 );
    tvp.setValue( t3, t4, 6.0 );
    tvp.setValue( t3, t6, 8.0 );

    EventSimulation sim = new EventSimulation( Collections.< Event >emptyList(), 1.0 );
    
    
    //TimeVaryingPlottableMap< Double > m = new 
    // TODO -- reproduce one of the animated plots with updating projections
  }

}