package edu.isi.techknacq.topic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import edu.isi.techknacq.util.ReadFile;
import edu.isi.techknacq.util.StrUtil;

/**
 *
 * @author linhong
 */
public class WordMatrixFormat {
    public void run(String dirname, String prefix) {
        ArrayList<String> filenames = StrUtil.initFolder(dirname);
        List myfile = new ArrayList<StringPair> (filenames.size());
        for (int i = 0; i < filenames.size(); i++) {
            String name = filenames.get(i);
            String word = name.substring(name.lastIndexOf("\\") + 1,
                                         name.length() - 4);
            String year = word.substring(1, 3);
            if (year.startsWith("0")) {
                year = "20" + year;
            } else
                year = "19" + year;
            System.out.println(year + word);
            StringPair o = new StringPair(year + word, filenames.get(i));
            myfile.add(o);
        }
        Collections.sort(myfile);
        ArrayList<String> posts = new ArrayList<String>(filenames.size());
        ReadFile myreader = new ReadFile();
        System.out.println(filenames.size());
        filenames.clear();
        for (int i = 0; i < myfile.size(); i++) {
            StringPair o = (StringPair)myfile.get(i);
            filenames.add(o.getWord().substring(4));
            String res = myreader.read(o.getname());
            posts.add(res);
            if (i % 1000 == 0)
                System.out.println(i);
        }
        System.out.println("Finish reading files.");
        WordModel mymodel = new WordModel();
        mymodel.initPost(posts);
        mymodel.computeWordModel();
        mymodel.saveWordModel("./lib/wordmodel.txt");
        mymodel.saveWord("./lib/words.txt");
        mymodel.saveTopK(30, "./lib/" + prefix + "top.csv");
        String []words = mymodel.getWords();
        int[]df = mymodel.getCount();
        System.out.println("Finish computing dictionary.");
        WordMatrix mymatrix = new WordMatrix();
        mymatrix.initWords(words);
        mymatrix.initWordFreq(df);
        mymatrix.initContent(posts);
        mymatrix.initmatrix("./lib/" + prefix + "wordmatrix.txt", filenames);
        mymodel.clear();
        mymatrix.clear();
        System.out.println("Finish document-to-word computation.");
    }

    public static void main(String []args) {
        if (args.length < 1) {
            System.err.println("Usage: [foldername] [prefixname]\n");
            System.exit(2);
        }
        WordMatrixFormat myrun = new WordMatrixFormat();
        myrun.run(args[0], args[1]);
    }
}
