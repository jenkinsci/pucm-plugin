package net.praqma;

import java.util.ArrayList;

class Component extends ClearBase
{
	private String fqobj     = null;
	private String shortname = null;
	private String pvob      = null;
	private String rootdir   = null;
	
	
	public Component( String fqobj, boolean trusted )
	{
		logger.trace_function();
		
		/* Prefix the object with component: */
		if( !fqobj.startsWith( "component:" ) )
		{
			fqobj = "component:" + fqobj;
		}
		
		this.fqobj   = fqobj;
		this.fqname  = fqobj;
		String[] res = TestComponent( fqobj );
		
		this.shortname = res[0];
		this.pvob      = res[1];
		
		if( !trusted )
		{
			// 'desc '.$fqobj
			Cleartool.run( "desc " + fqobj );
		}
	}
	
	public ArrayList<Baseline> GetBlsQueuedFor( Stream stream, String qlevel, int queue_level )
	{
		logger.trace_function();
		
		// cleartool_qx('lsbl -s -component '.$self->get_fqname().' -stream '.$stream->get_fqname());
		String cmd = "lsbl -s -component "  +this.GetFQName() + " -stream " + stream.GetFQName();
		String[] result = Cleartool.run_a( cmd );
		
		ArrayList<Baseline> qbls = new ArrayList<Baseline>();
		
		for( int i = 0 ; i < result.length ; i++ )
		{
			logger.debug( "CHW: Too many backslashes???" );
			Baseline bl = new Baseline( result[i].trim() + "\\@\\PDS_PVOB", false );
			
			/* CHW: NOTICE, the following function is NOT implemented(in the Baseline class)! */
			if( bl.QueuedForTest( queue_level ) )
			{
				qbls.add( bl );
			}
		}
		
		return qbls;
	}
	
	
	/**
	 * CHW: WORK IN PROGRESS. Needs Stream class implemented.
	 * @param stream
	 * @param plevel
	 * @param include_builds_in_progress
	 * @param newer_than_reccommended
	 */
	public void GetBlsWithPlevel( Stream stream, Plevel plevel, boolean include_builds_in_progress, boolean newer_than_reccommended )
	{
		logger.trace_function();
		
		ArrayList<Baseline> grb = stream.GetRecBls( false );
		if( grb.size() != 1 )
		{
			logger.error( "ERROR: Componet::get_bls_with_plevel( ). Didn't expect more than a single baseline as Recommended baseline on " + stream.GetFQName() );
			System.err.println( "ERROR: Componet::get_bls_with_plevel( ). Didn't expect more than a single baseline as Recommended baseline on " + stream.GetFQName() );
			System.exit( 1 );
		}
	}
	
	
	/* Not verified! */
	public String GetName()
	{
		logger.trace_function();
		
		return shortname;
	}
	
	public String GetShortName()
	{
		logger.trace_function();
		
		return shortname;
	}
	
	public String GetPvob()
	{
		logger.trace_function();
		
		return pvob;
	}
	
	public String GetRootDir()
	{
		logger.trace_function();
		
		if( rootdir == null )
		{
			// cleartool("desc -fmt %[root_dir]p ".$self->get_fqname());
			String cmd = "desc -fmt %[root_dir]p " + this.GetFQName();
			this.rootdir = Cleartool.run( cmd ).trim();
		}
		
		return rootdir;
	}
	
	public String GetAttr( String attr )
	{
		logger.trace_function();
		
		//cleartool('desc -s -aattr '.$params{attr}.' '.$self->get_fqname());
		String cmd = "desc -s -aattr " + attr + " " + this.GetFQName();
		
		/* CHW: Assuming the empty string will be returned, if the attribute is NOT set! */
		return Cleartool.run( cmd );
	}
	
	public String SetAttr( String attr, String value )
	{
		logger.trace_function();
		
		String replace = "";
		if( GetAttr( attr ).length() > 0 )
		{
			replace = " -replace";
		}
		
		//cleartool("mkattr ".$replace." ".$params{attr}.' '.$params{value}." ".$self->get_fqname());
		String cmd = "mkattr " + replace + " " + attr + " " + value + " " + this.GetFQName();
		return Cleartool.run( cmd );
	}

}