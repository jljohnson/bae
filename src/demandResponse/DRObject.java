/**
 * 
 */
package demandResponse;

import gov.nasa.jpl.ae.event.Duration;
import gov.nasa.jpl.ae.event.DurativeEvent;
import gov.nasa.jpl.ae.event.Timepoint;

/**
 * @author bclement
 *
 */
public class DRObject extends DurativeEvent {
  public enum ApplianceType {
    PCT(0.0,2000.0,20,Timepoint.Units.minutes,0.1,0.9,15,Timepoint.Units.hours),
    refrigerator(100.0,500.0,5,Timepoint.Units.minutes,0.01,0.15,15,Timepoint.Units.hours),
    EV(0.0,1000.0,5,Timepoint.Units.hours,0.0,1.0,2.5,Timepoint.Units.hours);
    
    /**
     * @param minPower
     * @param maxPower
     * @param durationAtMax
     * @param minPercentTimeAtMax
     * @param maxPercentTimeAtMax
     * @param timeOfDayAtMaxPercent
     */
    private ApplianceType( double minPower, double maxPower,
                           double durationAtMaxVal,
                           Timepoint.Units durationAtMaxUnits,
                           double minPercentTimeAtMax,
                           double maxPercentTimeAtMax,
                           double timeOfDayAtMaxPercentVal,
                           Timepoint.Units timeOfDayAtMaxPercentUnits ) {
      this.minPower = minPower;
      this.maxPower = maxPower;
      this.durationAtMax = new Duration( "durationAtMax",
                                         durationAtMaxVal, durationAtMaxUnits,
                                         null );
      this.minPercentTimeAtMax = minPercentTimeAtMax;
      this.maxPercentTimeAtMax = maxPercentTimeAtMax;
      this.timeOfDayAtMaxPercent = new Duration( "timeOfDayAtMaxPercent",
                                                 timeOfDayAtMaxPercentVal,
                                                 timeOfDayAtMaxPercentUnits,
                                                 null );
    }
    double minPower, maxPower;
    Duration durationAtMax = null; //new Duration( "duration at max", durVal, durUnits, this );
    double minPercentTimeAtMax, maxPercentTimeAtMax;
    Duration timeOfDayAtMaxPercent = null;
  } 
  
  protected ApplianceType applianceType = ApplianceType.PCT;
  
	/**
	 * 
	 */
	public DRObject() {
		super();
		startTime.setValue( 7200 ); // HACK -- assumes seconds and not wrt epoch
		duration.setValue( 7200 );
	}

  public double predictedLoadReduction( double t ) {
    return predictedLoadReduction( new Timepoint( "", (int)t, null ) );
  }
	public double predictedLoadReduction( Timepoint t ) {
	  if ( !t.isGrounded(false, null) || !isGrounded(false, null) ) return 0.0;
	  if ( t.getValue() < startTime.getValue() || t.getValue() > endTime.getValue() ) {
	    return 0.0;
	  }
	  int offsetTimeFromMax = applianceType.timeOfDayAtMaxPercent.getValue() - t.timeSinceMidnight();
	  boolean on = true;
	  int change = applianceType.durationAtMax.getValue() / 2;
	  int timeFromMax = change;
    final int lengthOfDay = Duration.lengthOfOne(Timepoint.Units.days);
	  while (offsetTimeFromMax > timeFromMax ) {
	    on = !on;
      change = (int)(((long)applianceType.durationAtMax.getValue()) * ( ( (double)(lengthOfDay - timeFromMax ) ) / lengthOfDay ) );
	    timeFromMax += change;
	  }
	  if ( on ) return applianceType.maxPower;
	  return applianceType.minPower;
	}
}
