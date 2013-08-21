package org.neo4j.test;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import static java.util.concurrent.TimeUnit.SECONDS;

public class OtherThreadRule<STATE> implements TestRule
{
    private final long timeout;
    private final TimeUnit unit;
    private volatile OtherThreadExecutor<STATE> executor;

    public OtherThreadRule()
    {
        this( 10, SECONDS );
    }

    public OtherThreadRule( long timeout, TimeUnit unit )
    {
        this.timeout = timeout;
        this.unit = unit;
    }

    public <RESULT> Future<RESULT> execute( OtherThreadExecutor.WorkerCommand<STATE, RESULT> cmd )
    {
        Future<RESULT> future = executor.executeDontWait( cmd );
        try
        {
            executor.awaitStartExecuting();
        }
        catch ( InterruptedException e )
        {
            throw new RuntimeException( "Interrupted while awaiting start of execution.", e );
        }
        return future;
    }

    protected STATE initialState()
    {
        return null;
    }

    public static Matcher<OtherThreadRule> isBlocked()
    {
        return new TypeSafeMatcher<OtherThreadRule>()
        {
            @Override
            protected boolean matchesSafely( OtherThreadRule rule )
            {
                try
                {
                    rule.executor.waitUntilWaiting();
                    return true;
                }
                catch ( TimeoutException e )
                {
                    return false;
                }
            }

            @Override
            public void describeTo( org.hamcrest.Description description )
            {
                description.appendText( "Thread blocked in state WAITING" );
            }
        };
    }

    @Override
    public String toString()
    {
        OtherThreadExecutor<STATE> otherThread = executor;
        if ( otherThread == null )
        {
            return "OtherThreadRule[state=dead]";
        }
        return otherThread.toString();
    }

    // Implementation of TestRule

    @Override
    public Statement apply( final Statement base, final Description description )
    {
        return new Statement()
        {
            @Override
            public void evaluate() throws Throwable
            {
                executor = new OtherThreadExecutor<>( description.getDisplayName(), timeout, unit, initialState() );
                try
                {
                    base.evaluate();
                }
                finally
                {
                    try
                    {
                        executor.shutdown();
                    }
                    finally
                    {
                        executor = null;
                    }
                }
            }
        };
    }
}