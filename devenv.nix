{
  pkgs,
  lib,
  config,
  inputs,
  ...
}: let
  unstable = import inputs.unstable-nixpkgs {
    inherit (pkgs.stdenv) system;
    config.allowUnfree = true; 
  };
in {
  cachix.enable = false;
  name = "metaos";

  languages.java = {
    enable = true;
    jdk.package = unstable.jdk25_headless;
    gradle.enable = true;
    gradle.package = unstable.gradle_9;
    lsp.enable = true;
  };
  packages = [
    pkgs.git
    pkgs.kubectl
    pkgs.kubectx
    pkgs.kubernetes-helm
    pkgs.kustomize
    pkgs.just
  ];

  env = {
    METAOS_ENV = "development";
  };

  enterShell = ''
  '';
}
