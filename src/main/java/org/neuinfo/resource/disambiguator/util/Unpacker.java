package org.neuinfo.resource.disambiguator.util;

import java.io.File;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Untar;

/**
 * 
 * @author bozyurt
 * 
 */
public class Unpacker extends Untar {

	public Unpacker(File archiveFile, File destDir) {
		super();
		setProject(new Project());
		setTaskName("untar");
		setSrc(archiveFile);
		setDest(destDir);
		String compressionScheme = "none";
		if (archiveFile.getName().endsWith(".tgz")
				|| archiveFile.getName().endsWith(".tar.gz")) {
			compressionScheme = "gzip";
		} else if (archiveFile.getName().endsWith(".bz2")) {
			compressionScheme = "bzip2";
		}
		UntarCompressionMethod utcm = new UntarCompressionMethod();
		utcm.setValue(compressionScheme);
		setCompression(utcm);
	}

	public void unpack() {
		super.execute();
	}
}
