#!/usr/bin/env perl
use 5.38.2;
use File::Find;
use File::Path qw(remove_tree);
use Getopt::Long;

my $skipDownload = 0;

GetOptions(
    "skipDownload:+" => \$skipDownload,
) or die "bad options.\n";

###############################################
# utils

sub trim ($a) {
    $a =~ s/\A\s+//;
    $a =~ s/\s+\z//;
    $a;
}

sub distinctWhiteSpaces ($a) {
    $a =~ s/\s+/ /g;
    $a;
}

sub sortUniq (@list) {
    my %seen;
    return grep { !$seen{$_}++ } sort @list;
}

sub cmd ($cmd) {
    $cmd = trim distinctWhiteSpaces $cmd;
    say "+$cmd";
    system $cmd;
    if ($?) {
        if ($? == -1) {
            die "failed to execute: $!";
        } elsif ($? & 127) {
            die sprintf "died with signal=%d, %s coreDump", ($? & 127), ($? & 128) ? 'w/' : 'w/o';
        } else {
            die sprintf "died with exitCode=%d", $? >> 8;
        }
    }
}

###############################################

say "# download workflow result …";

my $listCommand = trim distinctWhiteSpaces qq(
    gh run list 
        --workflow nativeBinaries-all.yml 
        --branch main 
        --status success 
        --limit 1 
        --json databaseId --jq '.[0].databaseId'
);

my $runId = trim scalar `$listCommand`;
$runId or die "missing runId.\n";

my $downloadDir = "nativeBinaries";
remove_tree($downloadDir);
cmd qq(gh run download '$runId' -n nativeBinaries-LinuxX64 --dir $downloadDir/LinuxX64);
cmd qq(gh run download '$runId' -n nativeBinaries-MingwX64 --dir $downloadDir/MingwX64);
cmd qq(gh run download '$runId' -n nativeBinaries-MacosArm64 --dir $downloadDir/MacosArm64);
cmd qq(gh run download '$runId' -n nativeBinaries-MacosX64 --dir $downloadDir/MacosX64);

###############################################

say "# find targetArch …";

my $myArch;

# TODO 実行中のマシンのアーキテクチャを以下のいずれかで表現する
# "linuxArm64",
# "linuxX64",
# "mingwX64",
# "macosArm64",
# "macosX64",

###############################################

say "# listing workflow result …";

# list of [targetArch,module,buildArch,filePath]
my @files;

find(
    {   no_chdir => 1,
        wanted   => sub {
            return if not -f;
            return if m|.dSYM/Contents/|;
            m|nativeBinaries/([^/]*)/([^/]*)/([^/]*)/|
              or die "unknwon file: $_\n";
            my ($buildArch, $module, $targetArch) = ($1, $2, $3);
            push @files, [ $targetArch, $module, $buildArch, $_ ];
        },
    },
    $downloadDir,
);

@files or die "missing workflow result.\n";

for (sort { $a->[0] cmp $b->[0] or $a->[1] cmp $b->[1] or $a->[2] cmp $b->[2] or $a->[3] cmp $b->[3] } @files) {
    my ($targetArch, $module, $buildArch, $filePath) = @$_;
    next if $targetArch ne $myArch;
    say "## run ",join(",", @$_);
    cmd qq('$filePath');
}
