/**
 * 
 */
package demandResponse;

import java.lang.reflect.Method;
import java.util.Random;

import gov.nasa.jpl.ae.event.Consumable;
import gov.nasa.jpl.ae.event.Parameter;
import gov.nasa.jpl.ae.event.ParameterListenerImpl;
import gov.nasa.jpl.ae.event.TimeVaryingMap;
import gov.nasa.jpl.ae.util.Debug;

/**
 * @author bclement
 *
 */
public class Customer extends ParameterListenerImpl {

  public boolean additive = false;
  public int offsetSecondsFromMidnight = 0;
  public int loadHorizon = 86400; // 1 day in seconds 
  public double maxLoad = 3.5;  // kW
  public double minLoadFraction = 0.2;
  public double varianceFactor = 0.15;  // this is not Gaussian variance
  public double chanceOfChange = 0.8;
  public double correctionFactor = 0.5;
  public int samplePeriod = 900; // may only support seconds!
  public long seed = System.currentTimeMillis();
  public Random numGen = null;
  
  /**
   * The customer load behavior over time.  Here's a gnuplot command for its basic shape over a 24 hour period:
   * <p>gnuplot
   * <p>plot [0:24][0:1] f(x)=(1-m)*(sin(2*pi*(x/24-0.4))/2+0.5)+m, m=0.2, p=0.3, pi=3.14159, f(x)
   */
  public TimeVaryingMap<Double> load = null;
  public Consumable additiveLoad = null;

  public Parameter< TimeVaryingMap< Double > > loadParameter = null;
  public Parameter< Consumable > additiveLoadParameter = null;
  public Method summerLoadMethod = null;
  public Method summerLoadDeltaMethod = null;

  /**
   * 
   */
  public Customer() {
    super();
    init();
  }

  /**
   * @param name
   */
  public Customer( String name ) {
    super( name );
    init();
  }
  
  void init() {
    this.numGen = new Random(seed);
    double defaultValue = summerLoad(0);
    load = new TimeVaryingMap< Double >( "load", getSummerLoadMethod(), this,
                                         samplePeriod, loadHorizon );
    additiveLoad = new Consumable( "additiveLoad", defaultValue,
                                   getSummerLoadDeltaMethod(), this,
                                   samplePeriod, loadHorizon,
                                   minLoadFraction*maxLoad/3.0,
                                   Double.POSITIVE_INFINITY);
    loadParameter =
        new Parameter< TimeVaryingMap< Double > >( "load", null, load, this );
    additiveLoadParameter =
        new Parameter< Consumable >( "load", null, additiveLoad, this );
    parameters.add( additiveLoadParameter );
  }

  /**
   * @param offsetSecondsFromMidnight
   * @param loadHorizon
   * @param maxLoad
   * @param minLoadFraction
   * @param seed
   */
  public Customer( int offsetSecondsFromMidnight, int loadHorizon,
                   double maxLoad, double minLoadFraction, long seed ) {
    super();
    this.offsetSecondsFromMidnight = offsetSecondsFromMidnight;
    this.loadHorizon = loadHorizon;
    this.maxLoad = maxLoad;
    this.minLoadFraction = minLoadFraction;
    this.seed = seed;
    init();
  }

  public double summerLoadProfile( int timeSecs ) {
    int t = offsetSecondsFromMidnight + timeSecs;
    double kWatts = ( maxLoad * ( 1 - minLoadFraction )
                      * ( Math.sin( 2 * Math.PI * ( t / ( 24 * 3600.0 ) - 0.4 ) )
                          / 2.0 + 0.5 ) + minLoadFraction );
    Debug.outln("summerLoadProfile(" + timeSecs + ") = " + kWatts );
    return kWatts;
  }
  
  public double summerLoad( int timeSecs ) {
    double g = numGen.nextGaussian();
    double d = numGen.nextDouble();
    double kWatts =
        summerLoadProfile( timeSecs )
            * ( 1 + Math.pow( d, 1 / varianceFactor ) );
    Debug.outln("summerLoad(" + timeSecs + ") = " + kWatts );
    return kWatts;
  }
  
  public double summerLoadDelta( int timeSecsBefore, Double kWattsBefore,
                                 int timeSecsNext ) {
    double kWattsDelta = 0;
    double kWattsPredictedBefore = summerLoadProfile( timeSecsBefore );
    double kWattsPredictedNext = summerLoadProfile( timeSecsNext );
    double kWattsVariedNext = summerLoad( timeSecsNext );
    double kWattsOffNominal = kWattsBefore - kWattsPredictedBefore;
    boolean change = ( numGen.nextDouble() < chanceOfChange );
    //double kWattsNext = ;
    kWattsDelta = change ? ( kWattsVariedNext - kWattsPredictedBefore 
                             - kWattsOffNominal * correctionFactor )
                         : 0;
//    Debug.outln( "summerLoadDelta(timeSecsBefore=" + timeSecsBefore
//                        + ", kWattsBefore=" + kWattsBefore + ", timeSecsAfter="
//                        + timeSecsNext + "): kWattsPredictedBefore="
//                        + kWattsPredictedBefore + "; kWattsPredictedNext="
//                        + kWattsPredictedNext + "; kWattsVariedNext="
//                        + kWattsVariedNext + "; kWattsOffNominal="
//                        + kWattsOffNominal + "; change=" + change
//                        + "; kWattsDelta=" + kWattsDelta );
    Debug.out( String.format( "summerLoadDelta(timeSecsBefore=%6d, kWattsBefore=%.2f"
                           + ", timeSecsAfter=%6d): kWattsPredictedBefore=%.2f"
                           + "; kWattsPredictedNext=%.2f; kWattsVariedNext=%.2f"
                           + "; kWattsOffNominal=%.2f; change=%b"
                           + "; kWattsDelta=%.2f%n", timeSecsBefore,
                       kWattsBefore, timeSecsNext, kWattsPredictedBefore,
                       kWattsPredictedNext, kWattsVariedNext, kWattsOffNominal,
                       change, kWattsDelta ) );
    return kWattsDelta;
  }
  
  public Method getSummerLoadMethod() {
    if ( summerLoadMethod == null ) {
      summerLoadMethod = null;
      try {
        summerLoadMethod = Customer.class.getMethod( "summerLoad", int.class );
      } catch ( NoSuchMethodException e ) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      } catch ( SecurityException e ) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    return summerLoadMethod;
  }
 
  public Method getSummerLoadDeltaMethod() {
    if ( summerLoadDeltaMethod == null ) {
      summerLoadDeltaMethod = null;
      try {
        summerLoadDeltaMethod =
            Customer.class.getMethod( "summerLoadDelta", int.class,
                                      Double.class, int.class );
      } catch ( NoSuchMethodException e ) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      } catch ( SecurityException e ) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    return summerLoadDeltaMethod;
  }
 
}