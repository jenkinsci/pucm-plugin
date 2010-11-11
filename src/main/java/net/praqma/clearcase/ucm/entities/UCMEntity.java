package net.praqma.clearcase.ucm.entities;

import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.praqma.clearcase.ucm.persistence.*;
import net.praqma.utils.Debug;

public abstract class UCMEntity extends UCM
{
	protected static Debug logger = Debug.GetLogger( false );
	
	private static final Pattern pattern_std_fqname     = Pattern.compile( "^(\\w+):(\\w+)@(\\\\\\w+)$" );
	private static final Pattern pattern_version_fqname = Pattern.compile( "^(\\w:[\\w\\\\\\.]+)@@([\\\\\\w]+)$" );
	protected static final Pattern pattern_tag_fqname   = Pattern.compile( "^tag@(\\w+)@(\\\\\\w+)$" );
	
	private static ClassLoader classloader = UCMEntity.class.getClassLoader();
		
	protected static final String filesep = System.getProperty( "file.separator" );
	protected static final String linesep = System.getProperty( "line.separator" );
	public static final String delim      = "::";
	
	protected static TagPool tp = TagPool.GetInstance();
	
	/* For future caching purposes */
	private static HashMap<String, UCMEntity> entities = new HashMap<String, UCMEntity>();
	
	public enum ClearcaseEntityType
	{
		Activity,
		Baseline,
		Component,
		Stream,
		Tag,
		Version,
		Undefined;
		
		static ClearcaseEntityType GetFromString( String type )
		{
			if( type.equalsIgnoreCase( "baseline" ) )
			{
				return Baseline;
			}
			else if( type.equalsIgnoreCase( "activity" ) )
			{
				return Activity;
			}
			else if( type.equalsIgnoreCase( "stream" ) )
			{
				return Stream;
			}
			else if( type.equalsIgnoreCase( "component" ) )
			{
				return Component;
			}
			
			return Undefined;
		}
	}
	
	public enum Plevel
	{
		REJECTED,
		INITIAL,
		BUILT,
		TESTED,
		RELEASED;
	}
	
	protected Plevel GetPlevelFromString( String str )
	{
		Plevel plevel = Plevel.INITIAL;
		
		try
		{
			plevel = Plevel.valueOf( str );
		}
		catch( Exception e )
		{
			/* Do nothing... */
		}
		
		return plevel;
	}
	
	/* Fields that need not to be loaded */
	protected String fqname            = "";
	protected String shortname         = "";
	protected ClearcaseEntityType type = ClearcaseEntityType.Undefined;
	protected String pvob              = "";
	
	/* Loadable standard fields */
	protected String user = "";
	
	protected boolean loaded = false;
	
	public UCMEntity( )
	{
		
	}
	
	private UCMEntity( String fqname, String shortname, ClearcaseEntityType type, String pvob )
	{
		this.fqname    = fqname;
		this.shortname = shortname;
		this.type      = type;
		this.pvob      = pvob;
	}
	
	
	public static UCMEntity GetEntity( String fqname ) throws UCMEntityException
	{
		return GetEntity( fqname, true, false );
	}
	
	public static UCMEntity GetEntity( String fqname, boolean trusted ) throws UCMEntityException
	{
		return GetEntity( fqname, trusted, false );
	}
	
	public static UCMEntity GetEntity( String fqname, boolean trusted, boolean cachable ) throws UCMEntityException
	{
		logger.debug( "GetEntity = " + fqname );
		
		/* If exists, get the entity from cache cache? */
		if( cachable )
		{
			if( entities.containsKey( fqname ) )
			{
				logger.log( "Fetched " + fqname + " from cache." );
				return entities.get( fqname );
			}
		}
		
		UCMEntity entity    = null;
		
		String shortname = "";
		ClearcaseEntityType type  = ClearcaseEntityType.Undefined;
		String pvob      = "";
				
		/* Test standard fully qualified names */
		Matcher match = pattern_std_fqname.matcher( fqname );
		if( match.find() )
		{
			ClearcaseEntityType etype = ClearcaseEntityType.GetFromString( match.group( 1 ) );
			
			/* Set the Entity variables */
			shortname = match.group( 2 );
			pvob      = match.group( 3 );
			type      = etype;
		}
		else
		{
			
			/* Not a standard entity, lets try Version */
			match = pattern_version_fqname.matcher( fqname );
			if( match.find() )
			{
				/* Set the Entity variables */
				shortname = match.group( 1 );
				pvob      = match.group( 2 );
				type      = ClearcaseEntityType.Version;
			}
			else
			{
				
				/* Not a Version entity, lets try Tag */
				match = pattern_tag_fqname.matcher( fqname );
				if( match.find() )
				{
					/* Set the Entity variables */
					shortname = match.group( 1 ); // This is also the eid
					pvob      = match.group( 2 );
					type      = ClearcaseEntityType.Tag;
				}
			}
		}
		
		/* If the entity is undefined, throw an exception  */
		if( type == ClearcaseEntityType.Undefined )
		{
			logger.error( "The entity type of " + fqname + " was not recognized" );
			throw new UCMEntityException( "The entity type of " + fqname + " was not recognized" );
		}
		
		/* Load the Entity class */
		Class<UCMEntity> eclass = null;
		try
		{	
			eclass = (Class<UCMEntity>) classloader.loadClass( "net.praqma.clearcase.ucm.entities." + type  );
		}
		catch ( ClassNotFoundException e )
		{
			logger.error( "The class " + type + " is not available." );
			throw new UCMEntityException( "The class " + type + " is not available." );
		}
		
		/* Try to instantiate the Entity object */
		try
		{
			entity = (UCMEntity)eclass.newInstance();
		}
		catch ( Exception e )
		{
			logger.error( "Could not instantiate the class " + type );
			throw new UCMEntityException( "Could not instantiate the class " + type );
		}
		
		/* Storing the variables in the object */
		entity.fqname    = fqname;
		entity.shortname = shortname;
		entity.type      = type;
		entity.pvob      = pvob;
		
		logger.log( "Created entity of type " + entity.type );
		
		entity.PostProcess();
		
		/* If not trusted, load the entity from the context */
		if( !trusted )
		{
			entity.Load();
		}
		
		if( cachable )
		{
			logger.log( "Storing " + fqname + " in cache." );
			entities.put( fqname, entity );
		}
		
		return entity;
	}
	
	protected void PostProcess()
	{
		/* NOP, should be overridden */
	}
	
	
	public void Load()
	{
		logger.warning( "Load method is not implemented for this Entity." );
		this.loaded = true;
	}
	

	/* Syntactic static helper methods for retrieving entity objects */
	
	public static Activity GetActivity( String name )
	{
		return GetActivity( name, true );
	}
	
	public static Activity GetActivity( String name, boolean trusted )
	{
		Activity entity = (Activity)UCMEntity.GetEntity( name, trusted );
		return entity;
	}


	public static Baseline GetBaseline( String name )
	{
		return GetBaseline( name, true );
	}
	
	public static Baseline GetBaseline( String name, boolean trusted )
	{
		Baseline entity = (Baseline)UCMEntity.GetEntity( name, trusted );
		return entity;
	}
	
	
	public static Component GetComponent( String name )
	{
		return GetComponent( name, true );
	}
	
	public static Component GetComponent( String name, boolean trusted )
	{
		Component entity = (Component)UCMEntity.GetEntity( name, trusted );
		return entity;
	}
	
	
	public static Stream GetStream( String name )
	{
		return GetStream( name, true );
	}
	
	public static Stream GetStream( String name, boolean trusted )
	{
		Stream entity = (Stream)UCMEntity.GetEntity( name, trusted );
		return entity;
	}
	
	/* Tag stuff */
	
	public Tag GetTag( String tagType, String tagID )
	{
		return tp.GetTag( tagType, tagID, this );
	}
	
	public Tag CreateTag( String tagType, String tagID, String timestamp, String buildStatus )
	{
		return tp.CreateTag( tagType, tagID, timestamp, buildStatus, this );
	}
	
	/* Getters */
	
	public String GetUser()
	{
		if( !loaded ) Load();
		return this.user;
	}
	
	public String GetFQName()
	{
		return this.fqname;
	}
	
	public String GetShortname()
	{
		return this.shortname;
	}
	
	public String GetPvob()
	{
		return this.pvob;
	}
	
	
	public String Stringify()
	{
		StringBuffer sb = new StringBuffer();
		
		sb.append( "----> " + this.fqname + " <----" + linesep );
		sb.append( "Shortname: " + this.shortname + linesep );
		if( !this.type.equals( ClearcaseEntityType.Version ) )
		{
			sb.append( "PVOB     : " + this.pvob + linesep );
		}
		sb.append( "Type     : " + this.type + linesep );
		
		return sb.toString();
	}
	
	public String toString()
	{
		return this.GetFQName();
	}
	
	
	public String GetXML()
	{
		return context.GetXML();
	}
	

	public void SaveState()
	{
		context.SaveState();
	}
}
