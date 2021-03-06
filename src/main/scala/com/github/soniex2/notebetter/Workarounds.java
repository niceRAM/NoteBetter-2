package com.github.soniex2.notebetter;

import com.github.soniex2.notebetter.api.NoteBetterInstrument;
import com.github.soniex2.notebetter.api.NoteBetterPlayEvent;
import com.github.soniex2.notebetter.api.soundevent.IAdvancedSoundEvent;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;

import java.util.*;

/**
 * @author soniex2
 */
public class Workarounds {
    public static class NoteTickWorkaround {
        private final BlockPos pos;
        private int note;
        private NoteBetterInstrument instrument;

        public NoteTickWorkaround(BlockPos pos, int note, NoteBetterInstrument instrument) {
            this.pos = pos;
            this.instrument = instrument;
            this.note = note;
        }

        @Override
        public int hashCode() {
            return ((note * 31 + instrument.hashCode()) * 31 + pos.hashCode()) * 31;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof NoteTickWorkaround)) return false;
            NoteTickWorkaround ntw = (NoteTickWorkaround) other;
            /* optimization */
            return ntw.pos.equals(pos) ? ntw.note == note ? ntw.instrument.equals(instrument) : false : false;
        }

        public void play(World world) {
            // Our event
            NoteBetterPlayEvent event = new NoteBetterPlayEvent(world, pos, world.getBlockState(pos), note, instrument);
            if (MinecraftForge.EVENT_BUS.post(event)) return;
            instrument = event.noteBetterInstrument();
            note = event.getVanillaNoteId();

            // Vanilla/Forge event
            /*NoteBlockEvent.Instrument vanillaInstrument = EventHelper.instrumentFromResLoc(instrument);
            if (vanillaInstrument != null) {
                NoteBlockEvent.Play e = new NoteBlockEvent.Play(world, pos, world.getBlockState(pos), note, vanillaInstrument.ordinal());
                if (MinecraftForge.EVENT_BUS.post(e)) return;
                vanillaInstrument = e.getInstrument();
                instrument = EventHelper.instrumentToResLoc(vanillaInstrument);
                note = e.getVanillaNoteId();
            }*/

            // Finally play
            new IAdvancedSoundEvent.Wrapper(instrument.soundEvent()).play(world, pos, SoundCategory.RECORDS, instrument.volume(), note);
        }
    }

    public static final Map<World, NoteTickWorkaroundWorldData> map = Collections.synchronizedMap(new WeakHashMap<World, NoteTickWorkaroundWorldData>());

    public static void addNoteTickWorkaround(World world, NoteTickWorkaround workaround) {
        NoteTickWorkaroundWorldData data;
        synchronized (map) {
            data = map.get(world);
            if (data == null)
                map.put(world, data = new NoteTickWorkaroundWorldData());
        }
        data.add(workaround);
    }

    // This needs to happen between the start of the tick and TileEntity updates, but also after block ticks.
    public static void sendNoteUpdates(World world) {
        NoteTickWorkaroundWorldData data = map.get(world);
        if (data == null) return;
        data.sendQueuedBlockEvents(world);
    }

    private static class NoteTickWorkaroundWorldData {
        private int blockEventCacheIndex = 0;
        @SuppressWarnings("unchecked")
        private List<NoteTickWorkaround>[] blockEventCaches = new ArrayList[] {
                new ArrayList<NoteTickWorkaround>(), new ArrayList<NoteTickWorkaround>()
        };

        public synchronized void add(NoteTickWorkaround workaround) {
            for (NoteTickWorkaround blockeventdata1 : blockEventCaches[blockEventCacheIndex]) {
                if (blockeventdata1.equals(workaround)) return;
            }

            blockEventCaches[blockEventCacheIndex].add(workaround);
        }

        private synchronized void sendQueuedBlockEvents(World w) {
            while (!blockEventCaches[blockEventCacheIndex].isEmpty()) {
                int i = blockEventCacheIndex;
                blockEventCacheIndex ^= 1;

                for (NoteTickWorkaround blockeventdata : blockEventCaches[i]) {
                    blockeventdata.play(w);
                }

                this.blockEventCaches[i].clear();
            }
        }
    }
}
