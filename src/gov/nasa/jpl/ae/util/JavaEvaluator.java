package gov.nasa.jpl.ae.util;

import gov.nasa.jpl.mbee.util.Debug;
import junit.framework.Assert;

/**
 * Evaluates a Java expression and returns the result. This uses
 * JavaToConstraintExpression, which builds a nested gov.nasa.jpl.ae.Expression
 * and tries to accommodate undefined classes and members, which would cause
 * problems here since we can't wait for them to be created.
 */
public class JavaEvaluator {

    protected JavaToConstraintExpression javaToConstraintExpression = new JavaToConstraintExpression( null );
    
    public JavaEvaluator( String packageName ) {
        javaToConstraintExpression.getClassData().setPackageName( packageName );
    }
    
    public static Object evaluate( String javaString ) {
        Debug.turnOn();
        return evaluate( javaString, null );
    }
    public static Object evaluate( String javaString, String packageName ) {
        Debug.turnOn();
//        try {
//            Thread.sleep( 10000 );
//        } catch ( InterruptedException e ) {
//            // TODO Auto-generated catch block
//            e.printStackTrace();
//        }
        JavaEvaluator evaluator = new JavaEvaluator( packageName );
        gov.nasa.jpl.ae.event.Expression< ? > expression =
                evaluator.javaToConstraintExpression.javaToAeExpression( javaString,
                                                                         null,
                                                                         false );
        Object result = expression.evaluate( true );
        return result;
    }
    
    
    /**
     * @param args
     */
    public static void main( String[] args ) {
        Debug.turnOn();
        Object result = JavaEvaluator.evaluate( "3+7-5" );
        Assert.assertTrue( result.equals( (Integer)( 3 + 7 - 5 ) ) );
        result = JavaEvaluator.evaluate( "\"foo\".substring(1)" );
        Assert.assertTrue( result.equals( "foo".substring( 1 ) ) );
        Debug.outln( "\nJavaEvaluator main() succeeeded!" );
        Debug.turnOff();
    }

}
