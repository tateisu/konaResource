#!/usr/bin/env perl
use 5.38.2;

my $resource_prefix = 'jp/juggler/konaArchive/native/';

my @required = qw(
      linux-aarch64/libkona_common_jni.so
      linux-x86_64/libkona_common_jni.so
      macos-universal/libkona_common_jni.dylib
      windows-x86_64/kona_common_jni.dll
);

my %resToJarFile;
my %jarFileToRes;

for my $jarFile (glob('workflowResult/*/common.jar')){
    open(my $fh, '-|', 'jar', 'tf', $jarFile) or die "$! $jarFile\n";
    while (my $entry = <$fh>) {
        next unless index($entry, $resource_prefix) == 0;
        my $resource = substr($entry, length($resource_prefix));
        chomp $resource;
        next unless $resource =~ m{\A[^/]+/[^/]+\.(?:so|dll|dylib)\z}i;
        $resToJarFile{$resource}{$jarFile} = 1;
        $jarFileToRes{$jarFile}{$resource} = 1;
    }
    close($fh) or die "$! $jarFile\n";
    my @resInJarFile = sort keys %{ $jarFileToRes{$jarFile} // {} };
    @resInJarFile or die "missing native DLLs in $jarFile\n";
    say "## $jarFile";
    for my $res (@resInJarFile){
        say "  $res";
    }
}

say "############################";
for my $resource (sort keys %resToJarFile) {
    my @files = sort keys %{ $resToJarFile{$resource} };
    say "found $resource in [", join(', ',@files), "]";
}

my @missing = grep{ not $resToJarFile{$_}} sort @required;
@missing and die "!!missing JNI libraries. [", join(', ',@missing), "]\n";
