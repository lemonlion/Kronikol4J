package io.kronikol.report.model;

import java.util.List;

/** A tree-structured step-parameter value (mirrors the .NET {@code TreeParameterValue} + {@code TreeNode}). */
public record TreeParameterValue(TreeNode root) {

    /** A node in a tree-structured step parameter. */
    public record TreeNode(String path, String node, String value, String expectation,
                           VerificationStatus status, List<TreeNode> children) {
        public TreeNode {
            children = children == null ? List.of() : List.copyOf(children);
        }
    }
}
