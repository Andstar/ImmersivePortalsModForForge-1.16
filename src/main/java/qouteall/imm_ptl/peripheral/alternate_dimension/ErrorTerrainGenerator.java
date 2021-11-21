package qouteall.imm_ptl.peripheral.alternate_dimension;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.Block;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.my_util.LimitedLogger;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.noise.OctaveSimplexNoiseSampler;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.Heightmap;
import net.minecraft.world.SpawnHelper;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ProtoChunk;
import net.minecraft.world.gen.ChunkRandom;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.StructuresConfig;
import net.minecraft.world.gen.chunk.VerticalBlockSample;
import net.minecraft.world.gen.feature.EndCityFeature;
import net.minecraft.world.gen.feature.MineshaftFeature;
import net.minecraft.world.gen.feature.OceanMonumentFeature;
import net.minecraft.world.gen.feature.StrongholdFeature;
import net.minecraft.world.gen.feature.StructureFeature;
import net.minecraft.world.gen.feature.WoodlandMansionFeature;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class ErrorTerrainGenerator extends ChunkGenerator {
    public static final Codec<ErrorTerrainGenerator> codec = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.LONG.fieldOf("seed").stable().forGetter(g -> g.worldSeed),
            ChaosBiomeSource.codec.fieldOf("biomeSource").stable().forGetter(o -> ((ChaosBiomeSource) o.getBiomeSource()))
        ).apply(instance, instance.stable(ErrorTerrainGenerator::new))
    );
    
    private final BlockState air = Blocks.AIR.getDefaultState();
    private final BlockState defaultBlock = Blocks.STONE.getDefaultState();
    private final BlockState defaultFluid = Blocks.WATER.getDefaultState();
    
    public static final int regionChunkNum = 4;
    public static final int averageY = 64;
    public static final int maxY = 128;
    
    private static final VerticalBlockSample verticalBlockSample = new VerticalBlockSample(
        0,
        Stream.concat(
            Stream.generate(Blocks.STONE::getDefaultState).limit(64),
            Stream.generate(Blocks.AIR::getDefaultState).limit(128 + 64)
        ).toArray(BlockState[]::new)
    );
    
    private long worldSeed;
    
    private final LoadingCache<ChunkPos, RegionErrorTerrainGenerator> cache = CacheBuilder.newBuilder()
        .maximumSize(10000)
        .expireAfterWrite(30, TimeUnit.SECONDS)
        .build(
            new CacheLoader<ChunkPos, RegionErrorTerrainGenerator>() {
                public RegionErrorTerrainGenerator load(ChunkPos key) {
                    return new RegionErrorTerrainGenerator(key.x, key.z, worldSeed);
                }
            });
    
    private final OctaveSimplexNoiseSampler surfaceDepthNoise;
    
    public ErrorTerrainGenerator(long seed, BiomeSource biomeSource) {
        super(biomeSource, new StructuresConfig(true));
        worldSeed = seed;
        
        surfaceDepthNoise = new OctaveSimplexNoiseSampler(
            new ChunkRandom(seed), IntStream.rangeClosed(-3, 0));
        
    }
    
    private static double getProbability(StructureFeature<?> structureFeature) {
        if (structureFeature instanceof StrongholdFeature) {
            return 0.0007;
        }
        if (structureFeature instanceof MineshaftFeature) {
            return 0.015;
        }
        if (structureFeature instanceof OceanMonumentFeature) {
            return 0.03;
        }
        if (structureFeature instanceof WoodlandMansionFeature) {
            return 0.08;
        }
        if (structureFeature instanceof EndCityFeature) {
            return 0.2;
        }
        return 0.15;
    }
    
    @Override
    public void populateEntities(ChunkRegion region) {
        ChunkPos chunkPos = region.getCenterPos();
        Biome biome = region.getBiome(chunkPos.getStartPos());
        ChunkRandom chunkRandom = new ChunkRandom();
        chunkRandom.setPopulationSeed(region.getSeed(), chunkPos.getStartX(), chunkPos.getStartZ());
        SpawnHelper.populateEntities(region, biome, chunkPos, chunkRandom);
    }
    
    public void doPopulateNoise(Chunk chunk) {
        ProtoChunk protoChunk = (ProtoChunk) chunk;
        ChunkPos pos = chunk.getPos();
        Heightmap oceanFloorHeightMap = protoChunk.getHeightmap(Heightmap.Type.OCEAN_FLOOR_WG);
        Heightmap surfaceHeightMap = protoChunk.getHeightmap(Heightmap.Type.WORLD_SURFACE_WG);
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        
        int regionX = Math.floorDiv(pos.x, regionChunkNum);
        int regionZ = Math.floorDiv(pos.z, regionChunkNum);
        RegionErrorTerrainGenerator generator = Helper.noError(() ->
            cache.get(new ChunkPos(regionX, regionZ))
        );
        
        for (int sectionY = 0; sectionY < 16; sectionY++) {
            ChunkSection section = protoChunk.getSection(sectionY);
            section.lock();
            
            for (int localX = 0; localX < 16; localX++) {
                for (int localY = 0; localY < 16; localY++) {
                    for (int localZ = 0; localZ < 16; localZ++) {
                        int worldX = pos.x * 16 + localX;
                        int worldY = sectionY * 16 + localY;
                        int worldZ = pos.z * 16 + localZ;
                        
                        BlockState currBlockState = generator.getBlockComposition(
                            worldX, worldY, worldZ
                        );
                        
                        if (currBlockState != air) {
                            section.setBlockState(localX, localY, localZ, currBlockState, false);
                            oceanFloorHeightMap.trackUpdate(localX, worldY, localZ, currBlockState);
                            surfaceHeightMap.trackUpdate(localX, worldY, localZ, currBlockState);
                        }
                    }
                }
            }
            
            section.unlock();
        }
        
    }
    
    
    @Override
    public int getWorldHeight() {
        return 256;
    }
    
    @Override
    public CompletableFuture<Chunk> populateNoise(Executor executor, StructureAccessor accessor, Chunk chunk) {
        ChunkSection[] sectionArray = chunk.getSectionArray();
//        ArrayList<ChunkSection> locked = new ArrayList<>();
//        for (ChunkSection chunkSection : sectionArray) {
//            if (chunkSection != null) {
//                chunkSection.lock();
//                locked.add(chunkSection);
//            }
//        }
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                doPopulateNoise(chunk);
            }
            catch (Throwable e) {
                e.printStackTrace();
            }
            return chunk;
        }, executor).thenApplyAsync((chunkx) -> {
//            for (ChunkSection chunkSection : locked) {
//                chunkSection.unlock();
//            }
            
            return chunkx;
        }, executor);
        
    }
    
    //if it's not 0, the sea biome will cause huge lag spike because of light updates
    //don't know why
    @Override
    public int getSeaLevel() {
        return 63;
    }
    
    @Override
    public int getHeight(int x, int z, Heightmap.Type heightmap, HeightLimitView world) {
        return 64;
    }
    
    @Override
    public VerticalBlockSample getColumnSample(int x, int z, HeightLimitView world) {
        return verticalBlockSample;
    }
    
    @Override
    protected Codec<? extends ChunkGenerator> getCodec() {
        return codec;
    }
    
    @Override
    public ChunkGenerator withSeed(long seed) {
        return new ErrorTerrainGenerator(seed, getBiomeSource().withSeed(seed));
    }
    
    private static final LimitedLogger limitedLogger = new LimitedLogger(5);
    
    @Override
    public void buildSurface(ChunkRegion region, Chunk chunk) {
        // copied from NoiseChunkGenerator
        try {
            ChunkPos chunkPos = chunk.getPos();
            int i = chunkPos.x;
            int j = chunkPos.z;
            ChunkRandom chunkRandom = new ChunkRandom();
            chunkRandom.setTerrainSeed(i, j);
            ChunkPos chunkPos2 = chunk.getPos();
            int k = chunkPos2.getStartX();
            int l = chunkPos2.getStartZ();
            double d = 0.0625D;
            BlockPos.Mutable mutable = new BlockPos.Mutable();
            
            for (int m = 0; m < 16; ++m) {
                for (int n = 0; n < 16; ++n) {
                    int o = k + m;
                    int p = l + n;
                    int q = chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE_WG, m, n) + 1;
                    double e = this.surfaceDepthNoise.sample(
                        (double) o * 0.0625D, (double) p * 0.0625D, 0.0625D,
                        (double) m * 0.0625D
                    ) * 15.0D;
                    int minSurfaceLevel = 10;
                    region.getBiome(mutable.set(k + m, q, l + n)).buildSurface(
                        chunkRandom, chunk, o, p, q, e,
                        this.defaultBlock, this.defaultFluid, this.getSeaLevel(),
                        minSurfaceLevel, region.getSeed()
                    );
                }
            }
            
            avoidSandLag(region);
        }
        catch (Throwable e) {
            limitedLogger.invoke(e::printStackTrace);
        }
    }
    
    //TODO carve more
    
    private static void avoidSandLag(ChunkRegion region) {
        Chunk centerChunk = region.getChunk(region.getCenterPos().x, region.getCenterPos().z);
        BlockPos.Mutable temp = new BlockPos.Mutable();
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                boolean isLastAir = true;
                for (int y = 0; y < 100; y++) {
                    temp.set(x, y, z);
                    BlockState blockState = centerChunk.getBlockState(temp);
                    Block block = blockState.getBlock();
                    if (block == Blocks.SAND || block == Blocks.GRAVEL) {
                        if (isLastAir) {
                            centerChunk.setBlockState(
                                temp,
                                Blocks.SANDSTONE.getDefaultState(),
                                true
                            );
                        }
                    }
                    isLastAir = blockState.isAir();
                }
            }
        }
    }
}
