// Aaron Yalong CS143B Project 2 (Virtual Memory Manager WITH Demand Paging)
// Works both WITH dp and WITHOUT dp

import java.io.*;
import java.util.*;

public class VirtualMemory {
    private static final int NUM_FRAMES = 1024;
    private static final int FRAME_SIZE = 512;
    private static final int MEMORY_SIZE = FRAME_SIZE * NUM_FRAMES;

    // Integer array is PM[512*1024] = PM[524288]
    private final int[] PM = new int[MEMORY_SIZE];

    // The disk (when implementing demand paging) is an integer array D[1024][512]
    private final int[][] disk = new int[NUM_FRAMES][FRAME_SIZE];

    // All VA and PA's are of typer integers in the ST and PT
    private final Map<Integer, Integer> ST = new HashMap<>();
    private final Map<Integer, Integer> PT = new HashMap<>();

    // Demand Paging free frames array
    private final boolean[] free = new boolean[NUM_FRAMES];

    // The VM manager initializes the PM from an initialization file consisting of 2 lines
    public void initialize(String initFile) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(initFile));

        // Get segment table entry commands
        String line1 = reader.readLine();
        String[] line1split = line1.split(" ");

        // Line 1 contains triples of integers, which define the contents of the ST
        for (int i = 0; i < line1split.length; i += 3) {
            // segment s
            int s = Integer.parseInt(line1split[i]);
            // length of segment s -> z
            int z = Integer.parseInt(line1split[i + 1]);
            // frame f
            int f = Integer.parseInt(line1split[i + 2]);

            // Size of field segment
            PM[2 * s] = z;

            // Frame number f
            PM[(2 * s) + 1] = f;

            // Demand paging
            if (f >= 0) {
                free[f] = false;
            }

            // Initialize in the Segment Table
            ST.put(2 * s, z);
            ST.put((2 * s) + 1, f);
        }

        // Get page table entry commands
        String line2 = reader.readLine();
        // Check if there is a next line
        if (line2 != null) {
            String[] line2split = line2.split(" ");

            // Line 2 contains triples of integers, which define the contents of the PTs
            for (int i = 0; i < line2split.length; i += 3) {
                // segment s
                int s = Integer.parseInt(line2split[i]);
                // page p of segment s
                int p = Integer.parseInt(line2split[i + 1]);
                // page p of segment s resides in frame f
                int f = Integer.parseInt(line2split[i + 2]);

                // Frame Number Initialized
                int STEntry = PM[(2 * s) + 1];

                // Finding the Starting Address
                if (STEntry >= 0) {
                    //  PT entry of page p: PM[2s+1]*512+p
                    int PTEntry = (FRAME_SIZE * STEntry) + p;

                    // Initialize PM and PT
                    PM[PTEntry] = f;
                    PT.put(p, f);

                // Handle negative values
                } else {
                    // Block number b
                    int b = -STEntry;
                    disk[b][p] = f;
                }
            }
        }
        reader.close();
    }

    // Process commands similar to Project 1
    public void commands(String inputFile, String outputFile) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(inputFile));
        BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile));

        String line;
        while ((line = reader.readLine()) != null) {
            String[] commands = line.trim().split("\\s+");
            if (commands.length == 0) continue;

            String command = commands[0];
            switch (command) {
                case "TA":
                    if (commands.length == 2) {
                        int va = translateVirtualAddress(Integer.parseInt(commands[1]));
                        writer.write(va + " ");
                    }
                    break;
                case "RP":
                    if (commands.length == 2) {
                        int pa = readPhysicalAddress(Integer.parseInt(commands[1]));
                        writer.write(pa + " ");
                    }
                    break;
                case "NL":
                    writer.newLine();
                    break;
                default:
                    break;
            }
        }
        reader.close();
        writer.close();
    }

    public VirtualMemory() {
        // Initialize and Fill Array
        Arrays.fill(free, true);
        // Reserve frames 0 and 1 for the segment table
        free[0] = false;
        free[1] = false;
    }

    private int translateVirtualAddress(int va) {
        // Address Translation
        int s = (va >> 18) & 0x1FF;
        int p = (va >> 9) & 0x1FF;
        int w = va & 0x1FF;
        int pw = va & 0x3FFFF;

        // Segment Index, Segment Limit, Page Table Frame
        int stIndex = 2 * s;
        int stLimit = PM[stIndex];
        int ptFrame = PM[stIndex + 1];

        // Outside segment boundary
        if (pw >= PM[2 * s]) {
            return -1;
        }

        // Invalid virtual address
        if ((w >= stLimit) || (PM[stIndex + 1] == 0)) {
            return -1;
        }

        // Page Table not resident in memory (DP)
        if (ptFrame < 0) {
            // Handle negative values for block number b
            int b = -ptFrame;

            // Get the available free frame
            int freeFrame = -1;
            for (int i = 2; i < free.length; i++) {
                if (free[i]) {
                    free[i] = false;
                    freeFrame = i;
                    break;}
            }
            if (freeFrame == -1) {
                throw new IllegalStateException("No free frames");
            }

            // Load Page Table in the free frame
            int startAddress = freeFrame * FRAME_SIZE;
            for (int i = 0; i < FRAME_SIZE; i++) {
                PM[startAddress + i] = disk[b][i];
            }
            ptFrame = freeFrame;
            PM[stIndex + 1] = ptFrame;
        }

        int ptIndex = ptFrame * FRAME_SIZE + p;
        int pFrame = PM[ptIndex];
        // Page not resident in memory (DP)
        if (pFrame < 0) {
            // Handle negative values for block number b
            int b = -pFrame;
            int freeFrame = -1;

            // Get the available free frame
            for (int i = 2; i < free.length; i++) {
                if (free[i]) {
                    free[i] = false;
                    freeFrame = i;
                    break;}
            }
            if (freeFrame == -1) {
                throw new IllegalStateException("No free frames");
            }

            // Load Page in the free frame
            int startAddress = freeFrame * FRAME_SIZE;
            for (int i = 0; i < FRAME_SIZE; i++) {
                PM[startAddress + i] = disk[b][i];
            }
            pFrame = freeFrame;
            PM[ptIndex] = pFrame;
        }

        // PA = PM[PM[2s + 1]*512 + p]*512 + w
        return pFrame * FRAME_SIZE + w;
    }

    private int readPhysicalAddress(int pa) {
        // Bound Check for invalid physical address
        if (pa >= MEMORY_SIZE) { return -1;}
        return PM[pa];
    }

    public static void main(String[] args) throws IOException {
        VirtualMemory VM = new VirtualMemory();
        // Testing with demand paging
        VM.initialize("init-dp.txt");
        VM.commands("input-dp.txt", "output-dp.txt");

        // Testing without demand paging
        VM.initialize("init-no-dp.txt");
        VM.commands("input-no-dp.txt", "output-no-dp.txt");
    }
}