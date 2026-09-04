#!/usr/bin/env perl
use 5.38.2;
use File::Basename qw(basename dirname);
use File::Copy qw(copy);
use File::Find qw(find);
use File::Path qw(make_path remove_tree);
use File::Spec;
use JSON::PP qw(decode_json);

my $result_root = 'workflowResult';
my $merged_root = 'workflowResult-merged';
my $merged_maven = "$merged_root/maven";
my $group = "$merged_maven/jp/juggler/konaResource";

my @hosts = qw(LinuxX64 MacosArm64 MingwX64);
my @required_modules = qw(
    common-linuxarm64
    common-linuxx64
    common-macosarm64
    common-mingwx64
);

sub copy_tree($source, $destination) {
    -d $source or die "missing directory: $source\n";
    make_path($destination);
    find({
        no_chdir => 1,
        wanted => sub {
            my $source_path = $File::Find::name;
            my $relative = File::Spec->abs2rel($source_path, $source);
            my $destination_path = File::Spec->catfile($destination, $relative);
            if (-d $source_path) {
                make_path($destination_path);
            } elsif (-f $source_path) {
                make_path(dirname($destination_path));
                copy($source_path, $destination_path)
                    or die "cannot copy $source_path to $destination_path: $!\n";
            } else {
                die "unsupported file type: $source_path\n";
            }
        },
    }, $source);
}

remove_tree($merged_root) if -e $merged_root;

my $macos_common = "$result_root/MacosArm64/maven/jp/juggler/konaResource/common";
copy_tree($macos_common, "$group/common");

for my $host (@hosts) {
    my $host_group = "$result_root/$host/maven/jp/juggler/konaResource";
    for my $source (
        glob("$host_group/common-linux*"),
        glob("$host_group/common-macos*"),
        glob("$host_group/common-mingw*"),
    ) {
        next unless -d $source;
        my $destination = "$group/" . basename($source);
        copy_tree($source, $destination) unless -e $destination;
    }
}

for my $module (@required_modules) {
    -d "$group/$module" or die "missing native publication: $module\n";
}

my @root_modules;
find({
    no_chdir => 1,
    wanted => sub {
        push @root_modules, $File::Find::name if -f $File::Find::name && /\.module\z/;
    },
}, "$group/common");
@root_modules or die "missing root Gradle module metadata\n";

open(my $fh, '<', $root_modules[0]) or die "$! $root_modules[0]\n";
local $/;
my $metadata = decode_json(<$fh>);
close($fh) or die "$! $root_modules[0]\n";

my %available_at = map {
    ref($_) eq 'HASH' && ref($_->{'available-at'}) eq 'HASH'
        ? ($_->{'available-at'}{module} // '' => 1)
        : ()
} @{ $metadata->{variants} // [] };

for my $module (@required_modules) {
    $available_at{$module}
        or die "root metadata does not reference native publication: $module\n";
}

say "Merged common Maven publications into $merged_maven";
