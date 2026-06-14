package com.leclowndu93150.foxy.loader;

import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.ITransformerVotingContext;
import cpw.mods.modlauncher.api.TargetType;
import cpw.mods.modlauncher.api.TransformerVoteResult;
import cpw.mods.modlauncher.api.ITransformer.Target;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Set;
import java.util.stream.Collectors;

public class FoxyAccessWidener implements ITransformer<ClassNode> {
    private static final int VISIBILITY_MASK = Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED | Opcodes.ACC_PUBLIC;

    private final AccessWidenerData data = AccessWidenerData.get();

    @Override
    public ClassNode transform(ClassNode node, ITransformerVotingContext context) {
        apply(node);
        return node;
    }

    @Override
    public TransformerVoteResult castVote(ITransformerVotingContext context) {
        return TransformerVoteResult.YES;
    }

    @Override
    public Set<Target<ClassNode>> targets() {
        return data.allTargets().stream().map(Target::targetClass).collect(Collectors.toSet());
    }

    @Override
    public TargetType<ClassNode> getTargetType() {
        return TargetType.CLASS;
    }

    @Override
    public String[] labels() {
        return new String[] { "foxy:access_widener" };
    }

    private void apply(ClassNode node) {
        String className = node.name.replace('/', '.');

        if (data.classes.contains(className)) {
            node.access = toPublic(node.access);
            for (var inner : node.innerClasses) {
                if (inner.name.equals(node.name)) {
                    inner.access = toPublic(inner.access);
                }
            }
        }

        Set<String> wideFields = data.fields.get(className);
        if (wideFields != null) {
            for (FieldNode field : node.fields) {
                if (wideFields.contains(field.name)) {
                    field.access = toPublic(field.access);
                }
            }
        }

        Set<String> wideMethods = data.methods.get(className);
        if (wideMethods != null) {
            for (MethodNode method : node.methods) {
                if (wideMethods.contains(method.name + method.desc)) {
                    method.access = toPublic(method.access);
                }
            }
        }
    }

    private static int toPublic(int access) {
        return (access & ~VISIBILITY_MASK) | Opcodes.ACC_PUBLIC;
    }
}
