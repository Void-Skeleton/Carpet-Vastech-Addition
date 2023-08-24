package carpet.commands;

import carpet.CarpetSettings;
import carpet.helpers.ClusterHelper;
import carpet.helpers.ClusterHelper.ClusterChunksReport;
import carpet.helpers.ClusterHelper.OptimalClusteringReport;
import net.minecraft.block.BlockDropper;
import net.minecraft.block.state.IBlockState;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.init.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class CommandCluster extends CommandCarpetBase {
    private final ClusterHelper clusterHelper = new ClusterHelper();

    @Override
    public String getName() {
        return "cluster";
    }

    /*
     * Usage:
     * /cluster set hashSize/clusterSize/clusterWidth/targetStart/targetEnd/clusterSearchStart/desiredHashStart <data>
     * /cluster compute optimalClustering/clusterChunks
     * /cluster construct loadCluster/makeClusterLoader
     */
    @Override
    public String getUsage(ICommandSender sender) {
        return "/cluster";
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos targetPos) {
        if (!CarpetSettings.commandCluster) return Collections.emptyList();
        switch (args.length) {
            case 0:
                return getListOfStringsMatchingLastWord(args, "set", "compute", "construct");
            case 1:
                switch (args[0]) {
                    case "set":
                        return getListOfStringsMatchingLastWord(args,
                                "hashSize", "clusterSize", "targetStart", "targetEnd",
                                "clusterWidth", "clusterSearchStart", "desiredHashStart");
                    case "compute":
                        return getListOfStringsMatchingLastWord(args, "optimalClustering", "clusterChunks");
                    case "construct":
                        return getListOfStringsMatchingLastWord(args, "loadCluster", "makeClusterLoader");
                }
        }
        return Collections.emptyList();
    }

    private static BlockPos getPosInChunk(int chunkX, int chunkZ, BlockPos pos) {
        BlockPos result = new BlockPos(chunkX * 16 + pos.getX(), pos.getY(), chunkZ * 16 + pos.getZ());
        return result;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 2) return;
        if (!command_enabled("commandCluster", sender)) return;
        switch (args[0]) {
            case "set": {
                switch (args[1]) {
                    case "hashSize": {
                        clusterHelper.setHashSize(Integer.parseInt(args[2]));
                        break;
                    }
                    case "clusterSize": {
                        clusterHelper.setClusterSize(Integer.parseInt(args[2]));
                        break;
                    }
                    case "clusterWidth": {
                        clusterHelper.setClusterWidth(Integer.parseInt(args[2]));
                        break;
                    }
                    case "targetStart": {
                        ChunkPos pos = new ChunkPos(Integer.parseInt(args[2]), Integer.parseInt(args[3]));
                        clusterHelper.setTargetStart(pos);
                        break;
                    }
                    case "targetEnd": {
                        ChunkPos pos = new ChunkPos(Integer.parseInt(args[2]), Integer.parseInt(args[3]));
                        clusterHelper.setTargetEnd(pos);
                        break;
                    }
                    case "clusterSearchStart": {
                        ChunkPos pos = new ChunkPos(Integer.parseInt(args[2]), Integer.parseInt(args[3]));
                        clusterHelper.setClusterSearchStart(pos);
                        break;
                    }
                    case "desiredHashStart": {
                        clusterHelper.setDesiredHashStart(Integer.parseInt(args[2]));
                        break;
                    }
                }
                break;
            }
            case "compute": {
                if (!sender.canUseCommand(2, "gamemode")) {
                    notifyCommandListener(sender, this, "/cluster construct requires OP permission");
                    return;
                }
                switch (args[1]) {
                    case "optimalClustering": {
                        OptimalClusteringReport optimalClusteringReport = clusterHelper.computeOptimalClustering();
                        if (optimalClusteringReport == null) {
                            notifyCommandListener(sender, this, "Not completely initialized! ");
                            return;
                        }
                        int targetX = optimalClusteringReport.target.x;
                        int targetZ = optimalClusteringReport.target.z;
                        notifyCommandListener(sender, this,
                                String.format("Optimal target chunk (%d, %d) with total clustering %d and hash start %d",
                                        targetX, targetZ,
                                        optimalClusteringReport.totalClustering, optimalClusteringReport.hashStart));
                        notifyCommandListener(sender, this,
                                "Preloaded chunks: " + optimalClusteringReport.preloadedChunks.stream().map(pos -> "(" + (targetX + pos.x) + ", " + (targetZ + pos.z) + ")").collect(Collectors.joining(", ")));
                        notifyCommandListener(sender, this, "Detailed clustering of the 21 clustered chunks: ");
                        for (ChunkPos pos: optimalClusteringReport.detailedClustering.keySet()) {
                            int clustering = optimalClusteringReport.detailedClustering.get(pos);
                            notifyCommandListener(sender, this, String.format("Chunk (%d, %d) has clustering %d", pos.x, pos.z, clustering));
                        }
                        break;
                    }
                    case "clusterChunks": {
                        int suboptimalRange = 5;
                        if (args.length >= 3) suboptimalRange = Integer.parseInt(args[2]);
                        OptimalClusteringReport optimalClusteringReport = clusterHelper.getLastOptimalClusteringReport();
                        ClusterChunksReport clusterChunksReport = clusterHelper.getClusterChunks(optimalClusteringReport, suboptimalRange);
                        if (clusterChunksReport == null) {
                            notifyCommandListener(sender, this, "Not completely initialized! ");
                            return;
                        }
                        notifyCommandListener(sender, this, "Cluster chunks has been generated");
                        ChunkPos target = optimalClusteringReport.target;
                        for (int hashStart: clusterChunksReport.hashStartToClusterChunks.keySet()) {
                            int suboptimalClustering = ClusterHelper.getTotalClustering(target, optimalClusteringReport.preloadedChunks,
                                    hashStart, clusterHelper.getClusterSize(), clusterHelper.getHashSize() - 1);
                            notifyCommandListener(sender, this, String.format("Hash start %d with total clustering %d and cluster grid length %d",
                                    hashStart, suboptimalClustering, clusterChunksReport.hashStartToMaxZ.get(hashStart) - clusterHelper.getClusterSearchStart().z + 1));
                        }
                        notifyCommandListener(sender, this, "Please choose one via /cluster set desiredHashStart <value>. ");
                        notifyCommandListener(sender, this, "You can observe the cluster in action via /cluster construct loadCluster then running /loadedChunks dump");
                        break;
                    }
                }
                break;
            }
            case "construct": {
                List<ChunkPos> clusterChunks = clusterHelper.getLastClusterChunksReport().hashStartToClusterChunks.get(clusterHelper.getDesiredHashStart());
                if (clusterChunks == null) {
                    notifyCommandListener(sender, this, "Not properly initialized! ");
                    return;
                }
                switch (args[1]) {
                    case "loadCluster": {
                        for (ChunkPos pos: clusterChunks) {
                            sender.getEntityWorld().getChunk(pos.x, pos.z);
                        }
                        break;
                    }
                    case "makeClusterLoader": {
                        int y = 150;
                        if (args.length >= 3) {
                            try {
                                y = Integer.parseInt(args[2]);
                            } catch (Throwable ignore) {
                            }
                        }
                        final int zStart = clusterHelper.getClusterSearchStart().z;
                        final int zEnd = clusterHelper.getLastClusterChunksReport().hashStartToMaxZ.get(clusterHelper.getDesiredHashStart());
                        final int xStart = clusterHelper.getClusterSearchStart().x;
                        final int xEnd = xStart + clusterHelper.getClusterWidth();
                        final World world = sender.getEntityWorld();
                        final BlockPos pos15y8 = new BlockPos(15, y, 8);
                        final BlockPos pos15y15 = new BlockPos(15, y, 15);
                        final BlockPos pos8y8 = new BlockPos(8, y, 8);
                        final BlockPos pos8yp8 = new BlockPos(8, y + 1, 8);
                        // clean trash
                        for (int x = xStart; x < xEnd; x ++) {
                            for (int z = zStart; z <= zEnd; z++) {
                                world.setBlockState(getPosInChunk(x, z, pos15y15), Blocks.AIR.getDefaultState(), 18);
                                world.setBlockState(getPosInChunk(x, z, pos15y8), Blocks.AIR.getDefaultState(), 18);
                                world.setBlockState(getPosInChunk(x, z, pos8y8), Blocks.AIR.getDefaultState(), 18);
                                world.setBlockState(getPosInChunk(x, z, pos8yp8), Blocks.AIR.getDefaultState(), 18);
                            }
                        }
                        // make the initial line that loads in the x direction
                        for (int x = xStart; x <= xEnd - 2; x++) {
                            world.setBlockState(getPosInChunk(x, zStart, pos15y8), Blocks.CHEST.getDefaultState(), 18);
                            world.getTileEntity(getPosInChunk(x, zStart, pos15y8));
                        }
                        // make the lines in the z direction that loads the cluster grid
                        for (int x = xStart; x <= xEnd - 1; x += 2) {
                            for (int z = zStart; z <= zEnd; z++) {
                                world.setBlockState(getPosInChunk(x, z, pos15y15), Blocks.CHEST.getDefaultState(), 18);
                                world.getTileEntity(getPosInChunk(x, z, pos15y15));
                            }
                        }
                        // place hoppers to keep the cluster chunks loaded
                        final IBlockState dropperState = Blocks.DROPPER.getDefaultState().withProperty(BlockDropper.FACING, EnumFacing.DOWN);
                        for (ChunkPos clusterPos : clusterChunks) {
                            int x = clusterPos.x;
                            int z = clusterPos.z;
                            world.setBlockState(getPosInChunk(x, z, pos8y8), Blocks.HOPPER.getDefaultState(), 18);
                            world.setBlockState(getPosInChunk(x, z, pos8yp8), dropperState, 18);
                            world.getTileEntity(getPosInChunk(x, z, pos8y8));
                            world.getTileEntity(getPosInChunk(x, z, pos8yp8));
                        }
                        notifyCommandListener(sender, this, "Constructed the loader");
                        break;
                    }
                }
                break;
            }
        }
    }
}
