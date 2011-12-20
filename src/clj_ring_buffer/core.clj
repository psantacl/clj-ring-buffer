(ns clj-ring-buffer.core
  (:use
   [clj-etl-utils.lang-utils :only [raise caused-by-seq]]))

(defprotocol RingBuffer
  (producer-exists! [this producer])
  (consumer-exists! [this consumer])
  (examine-num-producers [this])
  (examine-num-consumers [this])
  (examine-write-counts [this])
  (examine-read-counts [this])
  (peek [this])
  
  (empty-buffer? [this consumer])
  (full-buffer?  [this producer])
  (get [this consumer])
  (put [this producer val]))



(deftype RingBufferOpaque [size data num-producers num-consumers ^{:volatile-mutable true} read-counts ^{:volatile-mutable true} write-counts]
  RingBuffer
  
  (examine-write-counts [this]
    write-counts)
  
  (examine-read-counts [this]
    read-counts)

  (examine-num-producers [this]
    num-producers)

  (examine-num-consumers [this]
    num-consumers)

  (peek [this]
    (seq data))

  (producer-exists! [this producer]
    (if (or (< producer 1)  (> producer num-producers))
      (raise "Error:  producer %s DNE.  Only %s producers exists."
             producer (vec (range 1  (inc num-producers))))))

  (consumer-exists! [this consumer]
    (if (or (< consumer 1)  (> consumer num-consumers))
      (raise "Error:  consumer %s DNE.  Only %s consumers exists."
             consumer (vec (range 1  (inc num-consumers))))))
  
  (empty-buffer? [this consumer]
    (consumer-exists! this consumer)
    (let [reads           (read-counts consumer)
          next-read       (if (zero? reads)
                            consumer 
                            (+ reads num-consumers))
          target-producer   (let [target-producer (rem next-read num-producers)]
                              (if (zero? target-producer)
                                num-producers
                                target-producer))
          
          ;; (nth (mapcat identity
          ;;              (repeat (range 1 (inc num-producers))))
          ;;      (inc next-read))

          write-count     (write-counts target-producer)]
      (> next-read write-count)))
  
  #_(comment
    {1 1
     2 2
     3 3
     4 1}
    (or (and (= 12121 2) 2)
        (rem 1 2))
    
    (rem 1 3)
    (rem 2 3)
    (rem 3 3)
    (rem 4 3)
    (rem 5 3)
    (rem 6 3)
    )
  (full-buffer? [this producer]
    (producer-exists! this producer)
    (let [writes          (write-counts producer)
          next-write      (if (zero? writes)
                            producer
                            (+ writes num-producers))
          target-consumer   (let [target-consumer  (rem next-write num-consumers)]
                              (if (zero? target-consumer)
                                num-consumers
                                target-consumer))

          ;; (nth (mapcat identity
          ;;                                (repeat (range 1 (inc num-consumers))))
          ;;                        (inc next-write))


          ;; (inc (rem next-write num-consumers))
          read-count      (read-counts target-consumer)]
      (> (- next-write read-count) size)))
  
  (get [this consumer]
    (consumer-exists! this consumer)    
    (if (empty-buffer? this consumer)
      nil
      (let [reads           (read-counts consumer)
            next-read       (if (zero? reads)
                              consumer
                              (+ reads num-consumers))
            read-idx        (rem (dec next-read) size)]        
        (set! read-counts
              (assoc-in read-counts [consumer] next-read))
        (aget data read-idx))))
  
  
  
  (put [this producer val]
    (producer-exists! this producer)
    (if (full-buffer? this producer)     
      nil
      (let [writes          (write-counts producer)
            next-write      (if (zero? writes)
                              producer
                              (+ writes num-producers))
            ;; next-write      (+ (* writes num-producers) producer)
            write-idx       (rem (dec next-write) size)]
        (aset data write-idx val)
        (set! write-counts
              (assoc-in write-counts [producer] next-write))        
        true))))




(defn make-ring-buffer [num-producers num-consumers size]
  (if (or (<= size 0)
          (<= num-producers 0)
          (> num-producers size)
          (not= (rem size num-producers) 0)
          (<= num-consumers 0)
          (> num-consumers size)
          (not= (rem size num-consumers) 0))    
    (raise "Size must be an multiple of the number of producers(%s) and the number of consumers(%s)" num-producers num-consumers))
  (let  [write-counts   (zipmap (range 1 (inc num-producers)) (repeat 0))
         read-counts    (zipmap (range 1 (inc num-consumers)) (repeat 0))
         array          (object-array size)
         ring-buf       (RingBufferOpaque. size array num-producers num-consumers read-counts  write-counts)
         consumer-fns   (reduce
                         (fn [accum consumer]
                           (assoc accum (keyword (str "consumer-" consumer))
                                  (fn [] (get ring-buf consumer))))     
                         {}
                         (range 1 (inc num-consumers)))
         producer-fns  (reduce
                        (fn [accum producer]
                          (assoc accum  (keyword (str "producer-" producer))
                                 (fn [val] (put ring-buf producer val))))    
                        
                        consumer-fns
                        (range 1 (inc num-producers)))]
    
    (merge producer-fns consumer-fns {:ring-buffer ring-buf })))





(comment
  (def *rb* (make-ring-buffer 2 1 6))  

  (keys *rb*)
  (peek (:ring-buffer *rb*))

  ((:producer-1 *rb*) 69)
  ((:producer-2 *rb*) 1)
  ((:consumer-1 *rb*))
  )

