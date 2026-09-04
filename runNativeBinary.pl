#!/usr/bin/env perl

# nativeBinaries-all.yml ワークフローの結果をダウンロードして、
# ビルドターゲットがmrArchに一致するバイナリを全て実行する。

use 5.38.2;
use File::Find;
use File::Path qw(remove_tree);
use File::Spec;
use Config;
use Getopt::Long;

# undef to auto detect, else one of linuxX64, linuxArm64, mingwX64, macosArm64, macosX64
my $myArch;

# workflow yml that generate binaries.
my $workflowYml = "nativeBinaries-all.yml";

# folder download workflow result into.
my $downloadDir = "nativeBinaries";

# skip download step if flag is true and exists  $downloadDir
my $skipDownload = 0;

# it true, run workflow and wait successed.
my $runWorkflow = 0;
my $help = 0;

sub usage () {
    print <<'USAGE';
Usage: runNativeBinary.pl [options]

Downloads native binary workflow results and runs binaries matching the local architecture.

Options:
    --myArch ARCH       Target architecture to run
                        linuxX64, linuxArm64, mingwX64, macosArm64, macosX64
    --downloadDir DIR   Directory for downloaded workflow results
                        (default: nativeBinaries)
    --skipDownload      Skip downloading when the download directory exists
    --runWorkflow       Start the workflow and wait for it to complete
    --workflowYml FILE  Workflow file to run or query
                        (default: nativeBinaries-all.yml)
    -h, --help          Show this help
USAGE
}

GetOptions(
    "myArch=s"       => \$myArch,
    "downloadDir=s"  => \$downloadDir,
    "skipDownload"   => \$skipDownload,
    "runWorkflow"    => \$runWorkflow,
    "workflowYml=s" => \$workflowYml,
    "h|help"         => \$help,
) or do {
    print STDERR "bad options.\n";
    usage();
    exit 2;
};

if ($help) {
    usage();
    exit 0;
}

###############################################
# utils

sub trim ($a) {
    $a =~ s/\A\s+//;
    $a =~ s/\s+\z//;
    $a;
}

sub command_status ($status, $command) {
    if ($status == -1) {
        die "failed to execute $command: $!\n";
    } elsif ($status & 127) {
        die sprintf "%s died with signal=%d, %s coreDump\n",
            $command, ($status & 127), ($status & 128) ? 'w/' : 'w/o';
    }
    return $status >> 8;
}

sub command (@args) {
    say "+", join(" ", map { "'$_'" } @args);
    my $status = system @args;
    my $exit_code = command_status($status, $args[0]);
    die "$args[0] failed with exitCode=$exit_code\n" if $exit_code != 0;
}

sub capture_command (@args) {
    say "+", join(" ", map { "'$_'" } @args);
    open my $pipe, '-|', @args or die "failed to execute $args[0]: $!\n";
    local $/;
    my $output = <$pipe> // '';
    close $pipe;
    my $exit_code = command_status($?, $args[0]);
    die "$args[0] failed with exitCode=$exit_code\n" if $exit_code != 0;
    return $output;
}

###############################################

my $runId;
if($runWorkflow){
    say "# start workflow $workflowYml";
    my $output = capture_command('gh', 'workflow', 'run', $workflowYml, '--ref', 'main');
    $output =~ m|/runs/(\d+)| or die "$runId not found: $output\n";
    $runId = $1;

    say "# waiting end of runId=$runId";
    command('gh', 'run', 'watch', $runId, '--exit-status');
}else{
    say "# find latest success run of workflow $workflowYml";
    my $output = capture_command(
        'gh', 'run', 'list',
        '--workflow', $workflowYml,
        '--branch', 'main',
        '--status', 'success',
        '--limit', '1',
        '--json', 'databaseId',
        '--jq', '.[0].databaseId',
    );
    $runId = trim $output;
}

###############################################

if ($skipDownload && -d $downloadDir) {
    say "# download skipped.";
} else {
    say "# download workflow result …";
    $runId or die "missing runId.\n";
    remove_tree($downloadDir);
    command('gh', 'run', 'download', $runId, '-n', 'nativeBinaries-LinuxX64', '--dir', "$downloadDir/LinuxX64");
    command('gh', 'run', 'download', $runId, '-n', 'nativeBinaries-MingwX64', '--dir', "$downloadDir/MingwX64");
    command('gh', 'run', 'download', $runId, '-n', 'nativeBinaries-MacosArm64', '--dir', "$downloadDir/MacosArm64");
    command('gh', 'run', 'download', $runId, '-n', 'nativeBinaries-MacosX64', '--dir', "$downloadDir/MacosX64");
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
$myArch =~ /\A(?:linuxX64|linuxArm64|mingwX64|macosArm64|macosX64)\z/
  or die "unsupported myArch: $myArch\n";

###############################################

say "# listing workflow result …";

# list of [targetArch,module,buildArch,filePath]
my @files;

my $downloadRoot = File::Spec->rel2abs($downloadDir);
find(
    {   no_chdir => 1,
        wanted   => sub {
            return if not -f;
            return if m|[\\/]\.dSYM[\\/]Contents[\\/]|;
            my $relative = File::Spec->abs2rel($File::Find::name, $downloadRoot);
            my @parts = File::Spec->splitdir($relative);
            @parts >= 4 or die "unknown file: $File::Find::name\n";
            my ($buildArch, $module, $targetArch) = @parts[3, 1, 2];
            push @files, [ $targetArch, $module, $buildArch, $_ ];
        },
    },
    $downloadRoot,
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
            command($filePath, 'test');
        } else {
            command($filePath);
        }
    }
}

say "";
for (sort keys %skipped) {
    say "## skip targetArch $_";
}
