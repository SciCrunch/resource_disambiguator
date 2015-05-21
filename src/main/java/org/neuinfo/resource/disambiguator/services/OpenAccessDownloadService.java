package org.neuinfo.resource.disambiguator.services;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.log4j.Logger;

import org.neuinfo.resource.disambiguator.util.FtpClient;
import org.neuinfo.resource.disambiguator.util.Unpacker;
import org.neuinfo.resource.disambiguator.util.Utils;

/**
 * 
 * @author bozyurt
 * 
 */
public class OpenAccessDownloadService {
	private String indexRootDir;
	static Logger log = Logger.getLogger(OpenAccessDownloadService.class);

	public OpenAccessDownloadService() throws IOException {
		Properties props = Utils
				.loadProperties("resource_disambiguator.properties");

		indexRootDir = props.getProperty("index.rootdir");
	}

	public void getPMCArticles(String monthYear) throws Exception {
		File targetRoot = new File(indexRootDir, "PMC_OAI_" + monthYear);
		if (!targetRoot.isDirectory()) {
			targetRoot.mkdirs();
		}
		String[] remoteFiles = { "pub/pmc/articles.A-B.tar.gz",
				"pub/pmc/articles.C-H.tar.gz", "pub/pmc/articles.I-N.tar.gz",
				"pub/pmc/articles.O-Z.tar.gz" };

		for (String remoteFile : remoteFiles) {
			// just for testing 3GB zip file
			// if (remoteFile.indexOf("C-H") != -1) {
			int idx = remoteFile.lastIndexOf('/');
			assert idx != -1;
			String basename = remoteFile.substring(idx + 1);
			File localFile = new File(indexRootDir, basename);
			long diff, start;
			if (!localFile.exists()) {
				boolean downloaded = false;
				int numTries = 0;
				while (!downloaded && numTries < 2) {

					start = System.currentTimeMillis();
					FtpClient client = new FtpClient("ftp.ncbi.nlm.nih.gov");
					log.info("downloading " + remoteFile + " to " + localFile);
					client.transferFile(localFile.getAbsolutePath(),
							remoteFile, true);
					diff = System.currentTimeMillis() - start;
					log.info("Elapsed time (secs): " + (diff / 1000.0));
					if (localFile.isFile() && localFile.length() > 1000) {
						downloaded = true;
					} else {
						localFile.delete();
						log.info("Retrying ftp downloading for " + remoteFile);
						Thread.sleep(1000);
					}
					numTries++;
				}
				start = System.currentTimeMillis();
				log.info("unpacking " + localFile + " to " + targetRoot);
				Unpacker unpacker = new Unpacker(localFile, targetRoot);
				unpacker.unpack();
				diff = System.currentTimeMillis() - start;
				log.info("Elapsed time (secs): " + (diff / 1000.0));
			}
			// }
		}
		log.info("Finished downloading PMC articles for " + monthYear);
		log.info("---------------------------------------------------");
	}

	public static void usage(Options options) {
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp("OpenAccessDownloadService", options);
		System.exit(1);
	}

	public static void cli(String[] args) throws Exception {
		Option help = new Option("h", "print this message");
		Option myOption = Option.builder("d").required().hasArg()
				.argName("monthYear").desc("monthYear").build();
		Options options = new Options();
		options.addOption(help);
		options.addOption(myOption);

		CommandLineParser cli = new DefaultParser();
		CommandLine line = null;
		try {
			line = cli.parse(options, args);
		} catch (Exception x) {
			System.err.println(x.getMessage());
			usage(options);
		}

		if (line.hasOption("h")) {
			usage(options);
		}
		String monthYear = line.getOptionValue("d");
		OpenAccessDownloadService s = new OpenAccessDownloadService();
		s.getPMCArticles(monthYear);
	}

	public static void main(String[] args) throws Exception {
		// OpenAccessDownloadService s = new OpenAccessDownloadService();
		// s.getPMCArticles("201310");
		cli(args);
	}
}
