package net.praqma;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.praqma.Debug;

class ClearBase
{
	
	protected static final String delim    = "::";
	
	protected static final String rx_fqobj = "(.*)@(\\\\.*)$";
	
	protected static Debug logger = Debug.GetLogger();
	
	protected String fqname = null;
	
	private static final boolean isTest = true;
	private static boolean hudson = true;
	protected static AbstractCleartoolFactory CF = null;
	
	/**
	 * CHW: This is not the same as the Perl Plevel!!!
	 * Rejected is "0", not "4", because of a simpler implementation issue.
	 * @author wolfgang
	 *
	 */
	protected static enum Plevel
	{
		REJECTED,
		INITIAL,
		BUILT,
		TESTED,
		PLEVEL_RELEASED;
		
		String GetName()
		{
			return this.name();
		}
	}
	
	public ClearBase( )
	{
		if( isTest )
		{
			CF = CleartoolTestFactory.CFGet( hudson );
		}
		else
		{
			CF = CleartoolFactory.CFGet();
		}
	}
	
	protected static final String BUILD_IN_PROGRESS_ENUM_TRUE = "\"TRUE\"";
	protected static final String ATTR_BUILD_IN_PROGRESS      = "BuildInProgress";
	
	protected static final String filesep                     = System.getProperty( "file.separator" );
	protected static final String linesep                     = System.getProperty( "line.separator" );
	
	/**
	 * Test if a component is a fully qualified component in the format: baseline\@\\PVOB (not: $fqobj)
	 */
	public String[] TestComponent( String component )
	{
		logger.trace_function();
		
		Pattern pattern = Pattern.compile( rx_fqobj );
		Matcher matches = pattern.matcher( component );
		
		logger.debug( "I am matching for " + rx_fqobj + " on " + component );
		
		/* A match is found */
		if( matches.find() )
		{
			String res[] = new String[2];
			res[0] = matches.group( 1 );
			res[1] = matches.group( 2 );
			
			logger.debug( "0 = " + res[0] + ". 1 = " + res[1] );
			
			return res;
			
		}
		
		/* CHW: Should this generate an error? Like the following: */
		/* 			logger.log( "ERROR: Activity constructor: The first parameter ("+fqactivity+") must be a fully qualified activity in the format: activity\\@\\PVOB" + linesep, "error" );
			System.err.println( "ERROR: Activity constructor: The first parameter ("+fqactivity+") must be a fully qualified activity in the format: activity\\@\\PVOB" + linesep );
			System.exit( 1 ); */
		return null;
	}
	
	protected Plevel GetPlevelFromString( String level )
	{
		logger.trace_function();
		
		Plevel plevel;
		try
		{
			plevel = Plevel.valueOf( level );
		}
		catch( NumberFormatException e )
		{
			plevel = Plevel.INITIAL;
		}
		
		return plevel;
	}
	
	
	protected String GetFQName()
	{
		logger.trace_function();
		return this.fqname;
	}
}