/**
 * This script intentionally causes the script's class loader to never be
 * eligible for garbage collection because of the never-ending thread.
 *
 * Only used as an example of a "bad script."
 */
class NeverEndingThread extends Thread {
  @Override
  public void run() {
    try
    {
      while(true) {
        sleep(1000)
      }
    }
    catch(Exception e) {
      e.printStackTrace()
    }
    println("exiting thread")
  }
}

new NeverEndingThread().start()

"exited"
