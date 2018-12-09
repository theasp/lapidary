(ns lapidary.search-query-test
  (:require
   [lapidary.search-query :refer [query-parse]]
   [cljs.test :refer [deftest is]]))

(deftest query-parse-fail
  (is (= (query-parse "fail")
         {:index 4, :reason [{:tag :string, :expecting ":"}], :line 1, :column 5, :text "fail"})))

(deftest query-parse-blank
  (is (= (query-parse "")
         [:query])))

(deftest query-parse-integer
  (is (= (query-parse "a:1")
         [:query
          [:clause
           [:term
            [:record-keyword "a"]
            [:value
             [:integer "1"]]]]])))

(deftest query-parse-word
  (is (= (query-parse "a:text")
         [:query
          [:clause
           [:term
            [:record-keyword "a"]
            [:value
             [:word "text"]]]]])))

(deftest query-parse-string
  (is (= (query-parse "a:\"text text\"")
         [:query
          [:clause
           [:term
            [:record-keyword "a"]
            [:value
             [:string "text text"]]]]])))

(deftest query-parse-multiple
  (is (= (query-parse "a:1 b:2")
         [:query
          [:clause
           [:term
            [:record-keyword "a"]
            [:value
             [:integer "1"]]]]
          [:clause
           [:term
            [:record-keyword "b"]
            [:value
             [:integer "2"]]]]])))

(deftest query-parse-not
  (is (= (query-parse "!a:1")
         [:query
          [:clause
           [:not]
           [:term
            [:record-keyword "a"]
            [:value
             [:integer "1"]]]]])))

(deftest query-parse-grouping-or
  (is (= (query-parse "(or a:1 b:2)")
         [:query
          [:clause
           [:grouping
            [:or]]
           [:clause
            [:term
             [:record-keyword "a"]
             [:value
              [:integer "1"]]]]
           [:clause
            [:term
             [:record-keyword "b"]
             [:value
              [:integer "2"]]]]]])))

(deftest query-parse-grouping-and
  (is (= (query-parse "(and a:1 b:2)")
         [:query
          [:clause
           [:grouping
            [:and]]
           [:clause
            [:term
             [:record-keyword "a"]
             [:value
              [:integer "1"]]]]
           [:clause
            [:term
             [:record-keyword "b"]
             [:value
              [:integer "2"]]]]]])))

(deftest query-parse-grouping-nested
  (is (= (query-parse "(and a:1 b:2 (or c:3 d:4))")
         [:query
          [:clause
           [:grouping
            [:and]]
           [:clause
            [:term
             [:record-keyword "a"]
             [:value
              [:integer "1"]]]]
           [:clause
            [:term
             [:record-keyword "b"]
             [:value
              [:integer "2"]]]]
           [:clause
            [:grouping
             [:or]]
            [:clause
             [:term
              [:record-keyword "c"]
              [:value
               [:integer "3"]]]]
            [:clause
             [:term
              [:record-keyword "d"]
              [:value
               [:integer "4"]]]]]]])))


(deftest query-parse-integer-gt
  (is (= (query-parse "a:>1")
         [:query
          [:clause
           [:term
            [:record-keyword "a"]
            [:comparison
             [:gt]]
            [:value
             [:integer "1"]]]]])))
