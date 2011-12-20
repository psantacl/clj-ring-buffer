(ns clj-ring-buffer.test.core
  (:require [clj-ring-buffer.core :as rb] :reload)
  (:use [clojure.test]))

(defn assert-fullness [r-buffer]
  (doseq [i (range 1 (inc (rb/examine-num-producers r-buffer)))]    
    (is (rb/full-buffer? r-buffer i)))
  (doseq [i (range 1 (inc (rb/examine-num-consumers r-buffer)))]
    (is (not (rb/empty-buffer? r-buffer i)))))

(defn assert-empitness [r-buffer]
  (doseq [i (range 1 (inc (rb/examine-num-producers r-buffer)))]
    (println (format "full-buffer for producer %s => %s"
                     i
                     (rb/full-buffer? r-buffer i)))
    (is (not (rb/full-buffer? r-buffer i))))
  (doseq [i (range 1 (inc (rb/examine-num-consumers r-buffer)))]
    (println (format "empty-buffer for consumer %s => %s"
                     i
                     (rb/empty-buffer? r-buffer i)))
    (is (rb/empty-buffer? r-buffer i))))


(deftest size-must-be-positive
  (is (thrown? RuntimeException
               (rb/make-ring-buffer 1 1 -1))))

(deftest num-producers-must-be-positive
  (is (thrown? RuntimeException
               (rb/make-ring-buffer -1 1 1))))

(deftest size-must-be-multiple-of-num-producers
  (is (thrown? RuntimeException
               (rb/make-ring-buffer 3 1 2))))

(deftest cant-put-using-a-producer-which-dne
  (let [{r-buffer :ring-buffer} (rb/make-ring-buffer 1 1 2)]
    (is (thrown? RuntimeException
                 (rb/put r-buffer 2 "won't work")))
    (is (thrown? RuntimeException
                 (rb/put r-buffer 0 "won't work")))))

(deftest simple-get-and-put
  (let [{producer-1 :producer-1 consumer-1 :consumer-1 r-buffer :ring-buffer} (rb/make-ring-buffer 1 1 2)]
    (is (rb/empty-buffer? r-buffer 1))
    (producer-1 "A")
    (is (not (rb/empty-buffer? r-buffer 1)))
    (is (= "A" (consumer-1)))    
    (is (rb/empty-buffer? r-buffer 1))))

(deftest simple-loop-around
  (let [{producer-1 :producer-1 consumer-1 :consumer-1 r-buffer :ring-buffer } (rb/make-ring-buffer 1 1 2)]
    (producer-1 "A")
    (producer-1 "B")
    (assert-fullness r-buffer)
    
    (is (= "A" (consumer-1) ))    
    (is (not (rb/full-buffer? r-buffer 1)))
    (is (not (rb/empty-buffer? r-buffer 1)))
    
    (is (= "B" (consumer-1 )))
    (assert-empitness r-buffer)
    
    (producer-1 "C")
    (producer-1 "D")
    (assert-fullness r-buffer)

    (is (= '("C" "D")  (rb/peek r-buffer)))
    (is (= "C" (consumer-1)))
    (is (= "D" (consumer-1)))

    (assert-empitness r-buffer)))

;;Multiple Producers, One Consumer

(deftest multiple-producers-get-and-put
  (let [{producer-1 :producer-1 consumer-1 :consumer-1 producer-2 :producer-2 r-buffer :ring-buffer}
        (rb/make-ring-buffer 2 1 4)]
    (producer-1 "A")
    (producer-1 "B")
    
    (is (= '("A" nil  "B" nil)  (rb/peek r-buffer)))
    (is (not (rb/empty-buffer? r-buffer 1)))    
    (is (rb/full-buffer? r-buffer 1))
    (is (not (rb/full-buffer? r-buffer 2)))

    (producer-2 "i")
    (producer-2 "ii")

    (is (= '("A" "i"  "B" "ii") (rb/peek r-buffer)))
    (is (rb/full-buffer? r-buffer 2))
    
    (is (= "A" (consumer-1)))
    (is (= "i" (consumer-1)))
    (is (= "B" (consumer-1)))
    (is (= "ii" (consumer-1)))

    (assert-empitness r-buffer)
    (producer-1 "C")
    (is (not (rb/empty-buffer? r-buffer 1)))

    (producer-1 "D")
    (is (rb/full-buffer? r-buffer 1))
    (is (not (rb/full-buffer? r-buffer 2)))
    (is (= '("C" "i"  "D" "ii")  (rb/peek r-buffer)))

    (producer-2 "iii")
    (producer-2 "iv")
    (is (rb/full-buffer? r-buffer 2))
    (is (= '("C" "iii"  "D" "iv")  (rb/peek r-buffer)))))


(deftest multiple-consumers-get-and-put
  (let [{producer-1 :producer-1 consumer-1 :consumer-1 consumer-2 :consumer-2 r-buffer :ring-buffer }
        (rb/make-ring-buffer 1 2 4)]
    (producer-1 "A")
    (producer-1 "B")
    
    (is (= '("A" "B" nil  nil) (rb/peek r-buffer)))
    (is (not (rb/empty-buffer? r-buffer 1)))
    (is (not (rb/empty-buffer? r-buffer 2)))    
    (is (not (rb/full-buffer? r-buffer 1)))

    (producer-1 "i")
    (producer-1 "ii")

    (is (= '("A" "B" "i" "ii")  (rb/peek r-buffer)))
    (is (rb/full-buffer? r-buffer 1))
    
    (is (= "A" (consumer-1)))
    (is (= "i" (consumer-1)))
    (is (= "B" (consumer-2)))
    (is (= "ii" (consumer-2 )))

    (assert-empitness r-buffer)
    (producer-1 "C")
    (is (not (rb/empty-buffer? r-buffer 1)))
    (is (rb/empty-buffer? r-buffer 2))

    (producer-1 "D")
    (is (not (rb/empty-buffer? r-buffer 1)))
    (is (not (rb/empty-buffer? r-buffer 2)))    
    (is (not (rb/full-buffer? r-buffer 1)))
    (is (= '("C" "D"  "i" "ii")  (rb/peek r-buffer)))

    (producer-1 "iii")
    (producer-1 "iv")
    (is (rb/full-buffer? r-buffer 1))
    (is (=  '("C" "D" "iii" "iv") (rb/peek r-buffer)))))

(deftest multiple-producers-multiple-consumers
  (let [{producer-1 :producer-1 consumer-1 :consumer-1 producer-2 :producer-2 consumer-2 :consumer-2
         r-buffer :ring-buffer}
        (rb/make-ring-buffer 2 2 4)]

    (producer-1 "A")
    (producer-1 "B")
    
    (is (= '("A" nil "B"  nil) (rb/peek r-buffer)))
    (is (not (rb/empty-buffer? r-buffer 1)))
    (is (rb/empty-buffer? r-buffer 2))
    (is (rb/full-buffer? r-buffer 1))
    (is (not (rb/full-buffer? r-buffer 2)))

    (producer-2 "i")
    (producer-2 "ii")
    (is (= '("A" "i" "B" "ii")  (rb/peek r-buffer)))
    (assert-fullness r-buffer)
    (is (not (rb/empty-buffer? r-buffer 1)))
    (is (not (rb/empty-buffer? r-buffer 2)))
    
    (is (= "A" (consumer-1)))
    (is (= "B" (consumer-1)))
    (is (= "i" (consumer-2)))
    (is (= "ii" (consumer-2)))

    (assert-empitness r-buffer)

    (producer-1 "C")
    (is (not (rb/empty-buffer? r-buffer 1)))
    (is (rb/empty-buffer? r-buffer 2))

    (producer-2 "iii")
    (is (not (rb/empty-buffer? r-buffer 1)))
    (is (not (rb/empty-buffer? r-buffer 2)))
    
    (producer-1 "D")
    (producer-2 "iv")

    (is (= '("C"  "iii"  "D" "iv")  (rb/peek r-buffer)))
    (assert-fullness r-buffer)

    (is (= "C" (consumer-1)))
    (is (not (rb/empty-buffer? r-buffer 1)))

    (is (= "iii" (consumer-2)))
    (is (not (rb/empty-buffer? r-buffer 2)))
    
    (is (= "D" (consumer-1)))
    (is (rb/empty-buffer? r-buffer 1))

    (is (= "iv" (consumer-2)))
    (is (rb/empty-buffer? r-buffer 2))))


(comment

  (def *rb* (rb/make-ring-buffer 1 2 4))

  (do
    (rb/put *rb* 1 "A")    
    (rb/put *rb* 1 "B")
    
    (rb/put *rb* 1 "i")    
    (rb/put *rb* 1 "ii"))

  (rb/full-buffer? *rb* 1)

  (rb/get *rb* 1)
  (rb/get *rb* 2)
  (rb/empty-buffer? *rb* 1)
  
  (rb/peek *rb*)
  (run-tests 'clj-ring-buffer.test.core)

  )