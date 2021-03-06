package edu.isi.techknacq.graph;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

import infodynamics.measures.continuous.kernel.EntropyCalculatorKernel;

import edu.isi.techknacq.readinglist.ConceptToDoc;
import edu.isi.techknacq.readinglist.CitationGraph;
import edu.isi.techknacq.topic.IndexPair;
import edu.isi.techknacq.topic.WeightPair;
import edu.isi.techknacq.util.ReadWeightedTopicKey;


public class ComparisonOnAlledges {
    private ArrayList<String> keynames;
    private int tnum;
    private List []conceptsinword;
    public double[] flowscores;
    public double [][]flowmatrics;
    private EntropyCalculatorKernel entropy;
    private ArrayList<Double> topicscores;
    private Logger logger =
        Logger.getLogger(ComparisonOnAlledges.class.getName());

    public ComparisonOnAlledges() {
        entropy = new EntropyCalculatorKernel();
        entropy.initialise();
    }

    public void readKey(String filename) {
        ReadWeightedTopicKey myreader = new ReadWeightedTopicKey();
        myreader.read(filename,5);
        keynames = myreader.getKeyNames();
        myreader.conceptToWords(filename);
        conceptsinword = myreader.getConceptInWord();
        tnum = this.keynames.size();
        flowmatrics = new double[tnum][tnum];

        // Now that we know the number of topics, introduce default topic
        // scores in case they're not provided.
        topicscores = new ArrayList<Double>(tnum);
        for (int i = 0; i < tnum; i++) {
            topicscores.add(1.0);
        }
    }

    public void readTopicScore(String filename) {
        try {
            Scanner sc = new Scanner(new File(filename));
            while (sc.hasNext()) {
                //System.out.println(sc.next());
                topicscores.add(sc.nextDouble());
            }
        } catch (FileNotFoundException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    public void extract(List a1, double []v1) {
        Arrays.fill(v1, 0.0);
        for (int i = 0; i < a1.size(); i++) {
            WeightPair o = (WeightPair)a1.get(i);
            v1[o.getIndex()] = o.getWeight();
        }
    }

    public void extract2(List a1, double []v1) {
        Arrays.fill(v1, 0.0);
        for (int i = 0; i < a1.size(); i++) {
            IndexPair o = (IndexPair)a1.get(i);
            v1[o.getIndex()] = o.getWeight();
        }
    }

    public int getOccu(double []v1, double []v2) {
        int c = 0;
        int i = 0;
        int j = 0;
        while (i < v1.length && j < v2.length) {
            if (v1[i] > 0.0000000001 && v2[j] > 0.00000000001) {
                c++;
            }
            i++;
            j++;
        }
        return c;
    }

    public double entropy(double p) {
        if (p > 0.0)
            return -p * Math.log(p);
        else
            return 0.0;
    }

    // Adapted from cc.mallet.util.
    // Returns the KL divergence, K(p1 || p2).
    public double klDivergence(double[] p1, double[] p2) {
        double klDiv = 0.0;

        for (int i = 0; i < p1.length; ++i) {
            if (p1[i] < 0.000001) { continue; }
            if (p2[i] < 0.0000001) { continue; } // Limin

            klDiv += p1[i] * Math.log(p1[i] / p2[i]);
        }

        return klDiv / Math.log(2);
    }

    public double getDiffSim(double []v1, double []v2) {
        double res = 0.0;
        int nzero1 = 0;
        int nzero2 = 0;
        int a = 0;
        int b = 0;
        int i = 0;
        int j = 0;
        while (i < v1.length && j < v2.length) {
            if (v1[i] > 0.0000000001)
                nzero1++;
            if (v2[j] > 0.0000000001)
                nzero2++;
            if (v1[i] > 0.0000000001 && v2[j] > 0.00000000001) {
                if (v1[i] > v2[j]) {
                    a++;
                } else
                    b++;
            }
            i++;
            j++;
        }
        while (i < v1.length) {
            if (v1[i] > 0.0000000001)
                nzero1++;
            i++;
        }
        while (j < v2.length) {
            if (v2[j] > 0.0000000001)
                nzero2++;
            j++;
        }
        res = (double)((double)(a - b) * this.getOccu(v1, v2)
                       / (nzero1 + nzero2));
        return res;
    }

    public double getEntropy(double []v1, double []v2) {
        double ce;
        // System.out.println("res "+res);
        entropy.setObservations(v1);
        ce = entropy.computeAverageLocalOfObservations();
        entropy.setObservations(v2);
        ce -= entropy.computeAverageLocalOfObservations();
        // System.out.println();
        ce += this.klDivergence(v1, v2);
        ce -= this.klDivergence(v2, v1);
        // System.out.println("ce "+ce);
        return ce;
    }

    public double topSim(double []v1, double []v2) {
        double res;
        int i;
        int a = 0;
        int b = 0;
        for (i = 0; i < v1.length; i++) {
            if (v1[i] > 0.0000000001)
                a++;
        }
        for (i = 0; i < v2.length; i++) {
            if (v2[i] > 0.0000000001) {
                b++;
            }
        }
        int cooc = this.getOccu(v1, v2);
        if (a + b - cooc > 0)
            res = (double)cooc / (a + b - cooc);
        else
            res = 0.0;
        return res;
    }

    public void run(String filename, int K, String citationfile,
                    int maxfilewordnum) {
        List []conceptsindoc;
        try {
            ConceptToDoc doc = new ConceptToDoc();
            doc.initNum(tnum);
            doc.getTopK(K, filename);
            conceptsindoc = doc.getTopic2Doc();

            int cooccount;
            double informationflow;
            // double CEdoc;
            double CEword;
            double topicsim;
            double wordsim;
            double cocite;
            double hierdoc;
            double hierword;
            double citewang;
            double []v1 = new double[maxfilewordnum];
            double []v2 = new double[maxfilewordnum];
            double []v3 = new double[maxfilewordnum];
            double []v4 = new double[maxfilewordnum];

            FileWriter fstream = new FileWriter("alledge.tsv", false);
            BufferedWriter out = new BufferedWriter(fstream);
            out.write("sid\ts_topic\ttid\tt_topic\tsim_doc\tsim_word");
            out.write("\tinformation_flow\tCE\tcitation\tHier");
            out.write("\tCitation_Wang\n");

            CitationGraph mycite = new CitationGraph();
            mycite.setTopicNum(tnum);
            mycite.setMaxFileNum(maxfilewordnum);
            mycite.readCitation(citationfile);
            mycite.readC2D(filename);
            double [][]t2t = mycite.computeCitationLinks();

            // FileWriter fstream2 = new FileWriter("entropy1.txt", false);
            // BufferedWriter out2 = new BufferedWriter(fstream2);
            // FileWriter fstream3 = new FileWriter("entropy2.txt", false);
            // BufferedWriter out3 = new BufferedWriter(fstream3);

            for (int i = 0; i < tnum; i++) {
                if (this.topicscores.get(i) < 0.42)
                    continue;
                extract(conceptsindoc[i], v1);
                extract2(conceptsinword[i], v3);
                for (int j = 0; j < tnum; j++) {
                    if (this.topicscores.get(j) < 0.42)
                        continue;
                    if (j == i)
                        continue;
                    extract(conceptsindoc[j], v2);
                    extract2(conceptsinword[j], v4);
                    cooccount = this.getOccu(v1, v2);
                    if (cooccount > 2) {
                        topicsim = this.topSim(v1, v2);
                        wordsim = this.topSim(v3, v4);
                        // System.out.println(flowmatrics[i][j]);
                        if (flowscores[i] > flowscores[j])
                            informationflow = -this.flowmatrics[i][j];
                        else
                            informationflow = this.flowmatrics[i][j];
                        // CEdoc = this.getEntropy(v1, v2);
                        CEword = this.getEntropy(v3, v4);
                        hierdoc = this.getDiffSim(v1, v2);
                        hierword = this.getDiffSim(v3, v4);
                        cocite = (t2t[i][j] - t2t[j][i]) / this.getOccu(v1, v2);
                        CEword = CEword / 1.9514 + hierword / 1.1667;
                        citewang = t2t[i][j];

                        // if (Math.abs(CEword) > 0.007) {
                        //     if (CEword > 0) {
                        //         out2.write(i + "\t" + j + "\t" + CEword +
                        //                    "\n");
                        //     } else {
                        //         out2.write(j + "\t" + i + "\t" +
                        //                    (0 - CEword) + "\n");
                        //     }
                        // }
                        // if (Math.abs(cocite) > 10) {
                        //     if (cocite > 0) {
                        //         out3.write(i + "\t" + j + "\t" + cocite +
                        //                    "\n");
                        //     } else {
                        //         out3.write(j + "\t" + i + "\t" +
                        //                    (0 - cocite) + "\n");
                        //     }
                        // }
                        out.write(i + "\t" + this.keynames.get(i) + "\t" +
                                  j + "\t" + this.keynames.get(j) + "\t" +
                                  topicsim + "\t" + wordsim + "\t" +
                                  informationflow + "\t" +
                                  CEword + "\t" + cocite + "\t" +
                                  hierdoc + "\t" + citewang + "\n");
                        if (i % 10 == 0) {
                            System.out.println(i + "\t" + j + "\t" + wordsim +
                                               "\t" + informationflow + "\t" +
                                               CEword + "\t" + cocite + "\t" +
                                               hierdoc + "\t" + citewang);
                        }
                    }
                }
            }
            // out2.close();
            // out3.close();
            out.close();
        } catch (IOException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    public static void main(String []args) {
        if (args.length < 1) {
            System.out.println("Usage [keyfile] [tree file] " +
                "[topic composition file] [K] [citation file] " +
                "[flow file] ([topicscorefile] [maxfilewordnum])");
            System.exit(2);
        }
        ComparisonOnAlledges alledge = new ComparisonOnAlledges();
        alledge.readKey(args[0]);
        ReadFlowNetwork myreader = new ReadFlowNetwork();
        myreader.readKey(args[0]);
        alledge.flowscores = myreader.readFlowScore(args[1]);
        myreader.readFlowToMatrix(args[5], alledge.flowmatrics);

        if (args.length > 6)
            alledge.readTopicScore(args[6]);

        int maxfilewordnum = 400000;
        if (args.length > 7)
            maxfilewordnum = Integer.parseInt(args[7]);

        alledge.run(args[2], Integer.parseInt(args[3]), args[4],
                    maxfilewordnum);

        // alledge.readKey("mallet-keys-2gm-200.txt");
        // alledge.ReadInformationflowScore("mallet0702.tree");
        // alledge.run("concept2doc.txt", 200,"acl.txt");
    }
}
