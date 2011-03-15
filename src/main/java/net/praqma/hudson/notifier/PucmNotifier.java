package net.praqma.hudson.notifier;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.List;

import org.kohsuke.stapler.StaplerRequest;

import net.praqma.clearcase.ucm.UCMException;
import net.praqma.clearcase.ucm.entities.Baseline;
import net.praqma.clearcase.ucm.entities.Component;
import net.praqma.clearcase.ucm.entities.Cool;
import net.praqma.clearcase.ucm.entities.Stream;
import net.praqma.clearcase.ucm.entities.Tag;
import net.praqma.clearcase.ucm.entities.UCM;
import net.praqma.clearcase.ucm.entities.UCMEntity;
import net.praqma.clearcase.ucm.view.SnapshotView;
import net.praqma.clearcase.ucm.view.UCMView;
import net.praqma.hudson.exception.NotifierException;
import net.praqma.hudson.exception.ScmException;
import net.praqma.hudson.scm.PucmScm;
import net.praqma.hudson.scm.PucmState.State;
import net.praqma.util.debug.PraqmaLogger;
import net.praqma.util.debug.PraqmaLogger.Logger;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.FilePath.FileCallable;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.model.Hudson.MasterComputer;
import hudson.model.Node;

import hudson.remoting.Callable;
import hudson.remoting.Channel.Listener;
import hudson.remoting.DelegatingCallable;
import hudson.remoting.Future;
import hudson.remoting.Pipe;
import hudson.remoting.VirtualChannel;
import hudson.scm.SCM;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import net.sf.json.JSONObject;

/**
 * PucmNotifier perfoms the user-chosen PUCM post-build actions
 * 
 * @author Troels Selch S�rensen
 * @author Margit Bennetzen
 * 
 */
public class PucmNotifier extends Notifier
{
	private boolean promote;
	private boolean recommended;
	private Baseline baseline;
	private PrintStream hudsonOut;
	private Stream st;
	private boolean makeTag;
	private boolean setDescription;
	private Status status;

	private String id = "";
	
	private boolean ucmDeliver  = false;
	private boolean apply4level = true;
	//private boolean defaultTarget = true;
	private String alternateTarget;
	//private boolean createBaseline = true;
	String baselineName;

	private Logger logger = null;

	/**
	 * This constructor is used in the inner class <code>DescriptorImpl</code>.
	 * 
	 * @param promote
	 *            if <code>true</code>, the baseline will be promoted after the
	 *            build.
	 * @param recommended
	 *            if <code>true</code>, the baseline will be marked
	 *            'recommended' in ClearCase.
	 * @param makeTag
	 *            if <code>true</code>, pucm will set a Tag() on the baseline in
	 *            ClearCase.
	 */
	public PucmNotifier( boolean promote, boolean recommended, boolean makeTag, boolean setDescription, boolean ucmDeliver/*, boolean defaultTarget*/, String alternateTarget/*, boolean createBaseline*/, String baselineName, boolean apply4level )
	{
		this.promote         = promote;
		this.recommended     = recommended;
		this.makeTag         = makeTag;
		this.setDescription  = setDescription;
		
		/* Advanced */
		this.ucmDeliver      = ucmDeliver;
		this.apply4level     = apply4level;
		//this.defaultTarget   = defaultTarget;
		this.alternateTarget = alternateTarget;
		//this.createBaseline  = createBaseline;
		this.baselineName    = baselineName;
	}

	@Override
	public boolean needsToRunAfterFinalized()
	{
		return true;
	}

	public BuildStepMonitor getRequiredMonitorService()
	{
		return BuildStepMonitor.NONE;
	}

	@Override
	public boolean perform( AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener ) throws InterruptedException, IOException
	{
		logger = PraqmaLogger.getLogger();
		logger.trace_function();
		boolean result = true;
		hudsonOut = listener.getLogger();
		
		if ( build.getBuildVariables().get( "include_classes" ) != null )
		{
			String[] is = build.getBuildVariables().get( "include_classes" ).toString().split( "," );
			for( String i : is )
			{
				logger.subscribe( i.trim() );
			}
		}
		
		Cool.setLogger( logger );

		status = new Status();

		this.id = "[" + build.getParent().getDisplayName() + "::" + build.getNumber() + "]";

		SCM scmTemp = null;
		if ( result )
		{
			scmTemp = build.getProject().getScm();
			if ( !( scmTemp instanceof PucmScm ) )
			{
				listener.fatalError( "[PUCM] Not a PUCM scm. This Post build action can only be used when polling from ClearCase with PUCM plugin." );
				result = false;
			}
		}

		State pstate = null;
		if ( result )
		{
			// PucmScm scm = (PucmScm) scmTemp;
			// PucmState state = scm.getPucmState(
			// build.getParent().getDisplayName(), build.getNumber() );
			pstate = PucmScm.pucm.getState( build.getParent().getDisplayName(), build.getNumber() );

			// if ( scm.doPostbuild() )
			if ( pstate.doPostBuild() && pstate.getBaseline() != null )
			{
				logger.debug( id + "Post build" );

				String bl = pstate.getBaseline().GetFQName();

				/* If no baselines found bl will be null */
				if ( bl != null )
				{
					try
					{
						baseline = UCMEntity.GetBaseline( bl );
					}
					catch ( UCMException e )
					{
						logger.warning( id + "Could not initialize baseline." );
						baseline = null;
					}

					// st = scm.getStreamObject();
					st = pstate.getStream();
					if ( baseline == null )
					{
						// If baseline is null, the user has already been
						// notified
						// in Console output from PucmScm.checkout()
						result = false;
					}
				}
				else
				{
					result = false;
				}

			}
			else
			{
				// Not performing any post build actions.
				result = false;
			}
		}

		if ( result )
		{
			try
			{
				processBuild( build, launcher, listener, pstate );
				if ( setDescription )
				{
					build.setDescription( status.getBuildDescr() );
					hudsonOut.println( "[PUCM] Description set." );
				}

			}
			catch ( NotifierException ne )
			{
				hudsonOut.println( ne.getMessage() );
			}
			catch ( IOException e )
			{
				hudsonOut.println( "[PUCM] Couldn't set build description." );
			}
		}

		/*
		 * Removing baseline and job from collection, do this no matter what as
		 * long as the SCM is pucm
		 */
		if ( ( scmTemp instanceof PucmScm ) && baseline != null )
		{
			boolean done2 = pstate.remove();
			logger.debug( id + "Removing job " + build.getNumber() + " from collection: " + done2 );

			logger.debug( "PUCM FINAL=" + PucmScm.pucm.stringify() );
		}

		logger.debug( id + "The final state:\n" + pstate.stringify() );

		return result;
	}



	private void processBuild( AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener, State pstate ) throws NotifierException
	{
		Result buildResult = build.getResult();

		VirtualChannel ch = launcher.getChannel();

		if ( ch == null )
		{
			logger.debug( "The channel was null" );
		}
		
		FilePath workspace = build.getExecutor().getCurrentWorkspace();
		
		if( workspace == null )
		{
			logger.warning( "Workspace is null" );
			throw new NotifierException( "Workspace is null" );
		}
		
		try
		{
			logger.debug( id + "Trying to run remote tasks" );
			if( ucmDeliver )
			{
				logger.debug( id + "UCM deliver" );
				
				//String devstream = "pucm_" + System.getenv( "COMPUTERNAME" ) + "_" + build.getParent().getDisplayName() + "@" + pstate.getStream().GetPvob();
				//logger.debug( id + "Deliver to " + devstream );
				Future<Integer> i = null;
				try
				{
					//hudson.remoting.pi
					final Pipe pipe = Pipe.createRemoteToLocal();
					i = workspace.actAsync( new RemoteDeliver( buildResult, status, listener, alternateTarget, baselineName, pstate.getComponent().GetFQName(), pstate.getLoadModule(), pstate.getBaseline().GetFQName(), build.getParent().getDisplayName(), Integer.toString( build.getNumber() ), apply4level, logger, pipe ) );
					InputStream is = pipe.getIn();
					InputStreamReader isr = new InputStreamReader( is );
					BufferedReader br = new BufferedReader( isr );
					StringBuffer sb = new StringBuffer();
					while( !i.isDone() )
					{
						//sb.append( br.readLine() );
					}
					
					i.get();
					
					//logger.debug( "Der blev skrevet: " + sb.toString() );
				}
				catch( IOException e )
				{
					logger.warning( "COULD NOT DELIVER: " + e.getMessage() );
					logger.warning( e );
					throw new NotifierException( "Could not deliver" );
				}
				logger.debug( id + "UCM deliver DONE" );
			}

			logger.debug( id + "Remote post build step" );
			status = ch.call( new RemotePostBuild( buildResult, status, listener, makeTag, promote, recommended, pstate.getBaseline().GetFQName(), pstate.getStream().GetFQName(), build.getParent().getDisplayName(), Integer.toString( build.getNumber() ), logger ) );
			
			logger.empty( status.getLog() );
		}
		catch ( Exception e )
		{
			build.setResult( Result.UNSTABLE );
			logger.debug( id + "Something went wrong: " + e.getMessage() );
		}

		status.setBuildStatus( buildResult );

		if ( !status.isStable() )
		{
			build.setResult( Result.UNSTABLE );
		}
	}

	public boolean isPromote()
	{
		return promote;
	}

	public boolean isRecommended()
	{
		return recommended;
	}

	public boolean isMakeTag()
	{
		return makeTag;
	}

	public boolean isSetDescription()
	{
		return setDescription;
	}
	
	public boolean isUcmDeliver()
	{
		return ucmDeliver;
	}
	
	public boolean isApply4level()
	{
		return apply4level;
	}
	
	public String getBaselineName()
	{
		return baselineName;
	}
	
	public String getAlternateTarget()
	{
		return alternateTarget;
	}

	/**
	 * This class is used by Hudson to define the plugin.
	 * 
	 * @author Troels Selch S�rensen
	 * @author Margit Bennetzen
	 * 
	 */
	@Extension
	public static final class DescriptorImpl extends BuildStepDescriptor<Publisher>
	{
		public DescriptorImpl()
		{
			super( PucmNotifier.class );
			//logger.trace_function();
			load();
		}

		@Override
		public String getDisplayName()
		{
			//logger.trace_function();
			return "Praqmatic UCM";
		}

		/**
		 * Hudson uses this method to create a new instance of
		 * <code>PucmNotifier</code>. The method gets information from Hudson
		 * config page. This information is about the configuration, which
		 * Hudson saves.
		 */
		@Override
		public Notifier newInstance( StaplerRequest req, JSONObject formData ) throws FormException
		{
			//logger.trace_function();
			boolean promote         = req.getParameter( "Pucm.promote" ) != null;
			boolean recommended     = req.getParameter( "Pucm.recommended" ) != null;
			boolean makeTag         = req.getParameter( "Pucm.makeTag" ) != null;
			boolean setDescription  = req.getParameter( "Pucm.setDescription" ) != null;
			
			boolean ucmDeliver      = req.getParameter( "Pucm.ucmDeliver" ) != null;
			boolean apply4level     = req.getParameter( "Pucm.apply4level" ) != null;
			//boolean defaultTarget   = req.getParameter( "Pucm.default_target" ) != null;
			String alternateTarget  = req.getParameter( "Pucm.alternateTarget" );
			//boolean createBaseline  = req.getParameter( "Pucm.create_baseline" ) != null;
			String baselineName     = req.getParameter( "Pucm.baselineName" );
			save();
			return new PucmNotifier( promote, recommended, makeTag, setDescription, ucmDeliver/*, defaultTarget*/, alternateTarget/*, createBaseline*/, baselineName, apply4level );
		}

		@Override
		public boolean isApplicable( Class<? extends AbstractProject> arg0 )
		{
			//logger.trace_function();
			return true;
		}
	}
}
