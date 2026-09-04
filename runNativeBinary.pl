#!/usr/bin/env perl

# nativeBinaries-all.yml ワークフローの結果をダウンロードして、
# ビルドターゲットがmrArchに一致するバイナリを全て実行する。

use 5.38.2;
use File::Find;
use File::Path qw(remove_tree);
use Config;
use Getopt::Long;

# undef to auto detect, else one of linuxX64, linuxArm64, mingwX64, macosArm64, macosX64
my $myArch;

# folder douwnload workflow result into.
my $downloadDir = "nativeBinaries";

# skip download step if flag is true and exists  $downloadDir
my $skipDownload = 0;

GetOptions(
    "skipDownload:+" => \$skipDownload,
    "downloadDir=s"  => \$downloadDir,
    "myArch=s"       => \$myArch,
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

if ($skipDownload && -d $downloadDir) {
    say "# download skipped.";
} else {
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

    remove_tree($downloadDir);
    cmd qq(gh run download '$runId' -n nativeBinaries-LinuxX64 --dir $downloadDir/LinuxX64);
    cmd qq(gh run download '$runId' -n nativeBinaries-MingwX64 --dir $downloadDir/MingwX64);
    cmd qq(gh run download '$runId' -n nativeBinaries-MacosArm64 --dir $downloadDir/MacosArm64);
    cmd qq(gh run download '$runId' -n nativeBinaries-MacosX64 --dir $downloadDir/MacosX64);
}

###############################################

sub findMyArch() {

    my $machine = $Config{archname};

    if ($^O =~ /MSWin32/) {
        $machine =~ /(?:x86_64|amd64|x64)/i
          or die "unsupported machine architecture for mingwX64: $machine\n";
        return 'mingwX64';
    }

    my $arch;
    if ($machine =~ /(?:aarch64|arm64)/i) {
        $arch = 'Arm64';
    } elsif ($machine =~ /(?:x86_64|amd64)/i) {
        $arch = 'X64';
    } else {
        die "unsupported machine architecture: $machine\n";
    }

    return "linux$arch" if $^O eq "linux";
    return "macos$arch" if $^O eq "darwin";
    die "unsupported operating system: $^O\n";
}

if (not $myArch) {
    $myArch = findMyArch();
    say "# find myArch=$myArch";
}

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
my %skipped;
for (sort { $a->[0] cmp $b->[0] or $a->[1] cmp $b->[1] or $a->[2] cmp $b->[2] or $a->[3] cmp $b->[3] } @files) {
    my ($targetArch, $module, $buildArch, $filePath) = @$_;
    if ($targetArch ne $myArch) {
        $skipped{$targetArch} = 1;
    } else {
        say "\n## run ", join(",", @$_);

        # mingwでビルドした linuxX64 バイナリはパーミッションが設定されていない
        chmod(0755, $filePath) if not -x $filePath;

        if ($module eq 'test') {
            cmd qq('$filePath' test);
        } else {
            cmd qq('$filePath');
        }
    }
}

say "";
for (sort keys %skipped) {
    say "## skip targetArch $_";
}
