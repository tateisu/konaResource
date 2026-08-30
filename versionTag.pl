#!/usr/bin/env perl
use 5.38.2;
use IO::Prompter;

###############################################
sub cmd($cmd){
    say "+$cmd";
    system $cmd;
    if($?){
        if ($? == -1) {
            die "failed to execute: $!";
        }elsif ($? & 127) {
            die sprintf "died with signal=%d, %s coreDump",
                ($? & 127),
                ($? & 128) ? 'w/' : 'w/o';
        }else {
            die sprintf "died with exitCode=%d", $? >> 8;
        }
    }
}
sub trim($a){
    $a =~ s/\A\s+//;
    $a =~ s/\s+\z//;
    $a;
}

sub sortUniq(@list) {
    my %seen;
    return grep { !$seen{$_}++ } sort @list;
}

#######################################################

# ビルド設定からバージョン指定を読む
my %versions;
for (
    ["(rootProject)" => "properties"],
    ["common" => ":common:properties"],
    ["plugin" => ":plugin:properties"],
    ["sample1" => ":sample1:properties"],
) {
    my($module,$task) = @$_;
    my $version = trim scalar `./gradlew -q $task | grep '^version:' | cut -d' ' -f2-`;
    next if not length $version;
    next if $version eq 'unspecified';
    $versions{$module}=$version;
}
my @versions = sortUniq values %versions;
if( @versions !=1 ){
    say "versionが不統一: ", join ", ", map{ "'$_'"} @versions;
    for(sort keys %versions){
        say "$versions{$_} $_";
    }
    exit 1;
}

# ビルドを通ることを確認
cmd qq(./gradlew -PuseLocalArtifacts=true check sample1:runDebugExecutableLinuxX64 sample1:runReleaseExecutableLinuxX64);

# 未コミットまたは未追加の変更をチェック
my @lines = grep{ length $_} map{ trim $_ } `git status --porcelain --untracked-files=all`;
if(@lines){
    say "未コミットまたは未追加の変更があります";
    say $_ for @lines;
    exit 1;
}

my $version = $versions[0];
$version =~ /\A\d+\.\d+\.\d+\z/ or die "version format incorrect: [$version]";
say "version=[$version]";

# 現在のブランチ名をチェック
my $branch = trim scalar `git branch --show-current 2>&1`;
($branch eq "main") or die "現在のブランチがmainではありません。 $branch";

# バージョン番号に合わせたタグ
my $tag = "v$version";

# タグがまだ存在しないことを確認する
my $check = trim scalar `git tag --list '$tag' 2>&1`;
length($check) and die "tag already exists? $check";

my $answer = prompt(
    "version=$version, tag=$tag の作成とpushを行いますか? [y/N] ",
    -yn,
    -default => 'n',
);
$answer or die "否決されました\n";

# mainブランチのpush
cmd qq(git push origin main);
# タグの作成とpush
cmd qq(git tag -a '$tag' -m 'Release $version');
cmd qq(git push origin '$tag');
