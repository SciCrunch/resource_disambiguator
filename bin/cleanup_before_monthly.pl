#!/usr/bin/perl -w

use strict;
use Time::localtime;
use File::stat;
use Cwd;
use File::Basename;


my $TEST_MODE =  0;

my $cmd = undef;
my @tarballs = </var/indexes/articles*.tar.gz>; 
foreach my $tb (@tarballs) {
   $cmd = "mv -f $tb /var/disambiguator/indexes/";
   print "$cmd\n";
   system($cmd) unless($TEST_MODE);
}


my @pmc_dirs = </var/indexes/PMC_OAI*>;

my $max = -1;
my $the_pd = undef;
foreach my $pd (@pmc_dirs) {
   if (-d $pd) {
       if (stat($pd)->ctime > $max) {
	   $max = stat($pd)->ctime;
           $the_pd = $pd;
       }
     #  print "$pd creation time:", ctime(stat($pd)->ctime) , "\n";
   }
}
print "$the_pd creation time:", ctime(stat($the_pd)->ctime) , "\n";
my $pd_tarball = "${the_pd}.tgz";
$cmd = "tar czvf $pd_tarball $the_pd";
print "$cmd\n";
system($cmd) unless($TEST_MODE);
$cmd = "mv -f $pd_tarball /var/disambiguator/indexes/";
print "$cmd\n";
system($cmd) unless($TEST_MODE);

$cmd = "rm -rf $the_pd";
print "$cmd\n";
system($cmd) unless($TEST_MODE);

#$cmd = "mv -f $the_pd /var/disambiguator/indexes/";
#print "$cmd\n";
#system($cmd) unless($TEST_MODE);

