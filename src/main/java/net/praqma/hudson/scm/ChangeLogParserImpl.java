package net.praqma.hudson.scm;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.digester.Digester;
import org.xml.sax.SAXException;

import hudson.model.AbstractBuild;
import hudson.scm.ChangeLogParser;
import hudson.scm.ChangeLogSet;
import hudson.scm.ChangeLogSet.Entry;
import hudson.util.Digester2;

import net.praqma.util.debug.PraqmaLogger;
import net.praqma.util.debug.PraqmaLogger.Logger;

/**
 * 
 * @author Troels Selch
 * @author Margit Bennetzen
 * 
 */
public class ChangeLogParserImpl extends ChangeLogParser
{

	protected static Logger logger = PraqmaLogger.getLogger();

	@Override
	public ChangeLogSet<? extends Entry> parse( AbstractBuild build, File changelogFile ) throws IOException, SAXException
	{
		logger.trace_function();

		List<ChangeLogEntryImpl> entries = new ArrayList<ChangeLogEntryImpl>();

		// Source: http://wiki.hudson-ci.org/display/HUDSON/Change+log

		Digester digester = new Digester2();
		digester.push( entries );
		digester.addObjectCreate( "*/entry/activity", ChangeLogEntryImpl.class );
		digester.addSetProperties( "*/entry/activity" );
		digester.addBeanPropertySetter( "*/entry/activity/file", "nextFilepath" );
		digester.addBeanPropertySetter( "*/entry/activity/actName" );
		digester.addBeanPropertySetter( "*/entry/activity/author", "myAuthor" );
		digester.addSetNext( "*/entry/activity", "add" );
		FileReader reader = new FileReader( changelogFile );
		digester.parse( reader );
		reader.close();
		return new ChangeLogSetImpl( build, entries );
	}

}
