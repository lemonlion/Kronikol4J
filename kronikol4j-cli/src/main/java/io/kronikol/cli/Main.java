package io.kronikol.cli;

import java.util.Arrays;

/** Entry point for the Kronikol4J CLI. Currently dispatches the {@code merge} subcommand (§5.5). */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("merge")) {
            System.exit(MergeCommand.run(Arrays.copyOfRange(args, 1, args.length), System.out, System.err));
        }
        System.err.println("Usage: kronikol4j <command> [args]");
        System.err.println("Commands:");
        System.err.println("  merge   combine report fragments from sharded runners into one HTML report");
        System.exit(args.length == 0 ? 0 : 2);
    }
}
