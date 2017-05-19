package core.util

import java.util.concurrent.locks.Lock

import com.google.common.util.concurrent.Striped

/**
  * Класс, служащий для ограничения доступа к какому-либо ресурсу по ключу.
  * Например, если нужно добавить запись в БД с уникальным ключом, но только одну, например,
  * это может быть пара int'овых значений, описанная в виде tuple (fooId, barId).
  *
  * По сути, это обёртка над Striped[Lock], созданная для удобной работы с ним в scala.
  * Его лучше инициализировать через объект LockSet.
  *
  * @param striped Внутреннее хранилище.
  * @tparam K Тип ключей.
  */
class LockSet[K](striped: Striped[Lock]) {

  def withLock[R](key: K)(synchronizedBlock: => R): R = {
    val lock: Lock = synchronized(striped.get(key))
    try {
      lock.lock()
      synchronizedBlock
    }
    finally lock.unlock()
  }
}


object LockSet {
  /**
    * Creates a LockSet with eagerly initialized, strongly referenced locks, with the
    * specified fairness. Every lock is reentrant.
    *
    * @param stripes the minimum number of stripes (locks) required
    * @return a new { @code Striped<Lock>}
    */
  def lock[K](stripes: Int) = new LockSet[K](Striped.lock(stripes))

  /**
    * Creates a LockSet with lazily initialized, weakly referenced locks, with the
    * specified fairness. Every lock is reentrant.
    *
    * @param stripes the minimum number of stripes (locks) required
    * @return a new { @code Striped<Lock>}
    */
  def lazyWeakLock[K](stripes: Int) = new LockSet[K](Striped.lazyWeakLock(stripes))
}
