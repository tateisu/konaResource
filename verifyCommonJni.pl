#!/usr/bin/env perl
use 5.38.2;
use File::Spec;
use File::Temp qw(tempdir);
use Cwd qw(getcwd);

my $resource_prefix = 'jp/juggler/konaArchive/native/';

my @required = qw(
      linux-aarch64/libkona_common_jni.so
      linux-x86_64/libkona_common_jni.so
      macos-universal/libkona_common_jni.dylib
      windows-x86_64/kona_common_jni.dll
);

######################################################

sub read_exact {
    my ($fh, $length) = @_;
    my $data = '';
    while (length($data) < $length) {
        my $read = read($fh, my $chunk, $length - length($data));
        defined($read) or die "read error. $!\n";
        $read > 0 or die "unexpected end.\n";
        $data .= $chunk;
    }
    return $data;
}

sub verify_macos_universal($jarFile, $entry) {
    my $absoluteJarFile = File::Spec->rel2abs($jarFile);
    my $temporaryDirectory = tempdir('verify-common-jni-XXXXXX', TMPDIR => 1, CLEANUP => 1);
    my $currentDirectory = getcwd();
    chdir($temporaryDirectory) or die "cannot extract $jarFile: $!\n";
    my $exitCode = system('jar', 'xf', $absoluteJarFile, $entry);
    chdir($currentDirectory) or die "cannot restore working directory: $!\n";
    $exitCode == 0 or die "cannot extract $entry from $jarFile\n";

    my @entryParts = split m{/}, $entry;
    my $libraryFile = File::Spec->catfile($temporaryDirectory, @entryParts);
    open(my $fh, '<:raw', $libraryFile) or die "$! $jarFile:$entry\n";
    my $magic = unpack('N', read_exact($fh, 4));
    my $littleEndian;
    if ($magic == 0xcafebabe) {
        $littleEndian = 0;
    } elsif ($magic == 0xbebafeca) {
        $littleEndian = 1;
    } elsif ($magic == 0xcafebabf || $magic == 0xbfbafeca) {
        $littleEndian = ($magic == 0xbfbafeca);
    } else {
        die "$jarFile:$entry is not a macOS universal binary\n";
    }

    my $unpack_u32 = sub {
        unpack($littleEndian ? 'V' : 'N', $_[0]);
    };
    my $architectureCount = $unpack_u32->(read_exact($fh, 4));
    my $architectureSize = ($magic == 0xcafebabf || $magic == 0xbfbafeca) ? 32 : 20;
    my %architectures;
    for (1 .. $architectureCount) {
        my $architecture = read_exact($fh, $architectureSize);
        my $cpuType = $unpack_u32->(substr($architecture, 0, 4));
        $architectures{x86_64} = 1 if $cpuType == 0x01000007;
        $architectures{arm64} = 1 if $cpuType == 0x0100000c;
    }
    close($fh) or die "$! $jarFile:$entry\n";

    my @missingArchitectures = grep { not $architectures{$_} } qw(x86_64 arm64);
    @missingArchitectures and die "$jarFile:$entry is missing macOS architectures. [",
        join(', ', @missingArchitectures), "]\n";
    say "  $jarFile :verified contains x86_64 and arm64";
}

###########################################################

my %resToJarFile;
my %jarFileToRes;

my @inFiles = @ARGV;
@inFiles or @inFiles = glob('workflowResult/*/common.jar');

for my $jarFile (@inFiles){
    say "## $jarFile";
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
    for my $res (@resInJarFile){
        say "  $res";
    }
}

say "############################";
for my $resource (sort keys %resToJarFile) {
    my @files = sort keys %{ $resToJarFile{$resource} };
    say "found $resource in [", join(', ',@files), "]";
    if( $resource =~ /macos/i ){
        for my $jarFile (@files) {
            verify_macos_universal($jarFile, "$resource_prefix$resource");
        }
    }
}

my @missing = grep{ not $resToJarFile{$_}} sort @required;
@missing and die "!!missing JNI libraries. [", join(', ',@missing), "]\n";
