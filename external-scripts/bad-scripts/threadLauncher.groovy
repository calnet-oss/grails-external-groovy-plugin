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
