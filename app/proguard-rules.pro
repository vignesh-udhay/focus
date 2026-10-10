# WorkManager 2.7.1, which Glance brings in, keeps its input mergers with
# `-keep class * extends androidx.work.InputMerger` and names no constructor.
# R8 in full mode then keeps the class and drops `<init>()`, WorkManager cannot
# instantiate OverwritingInputMerger, every job fails, and the home-screen
# widget never leaves its loading state. Seen on a release build on a OnePlus 8T.
-keep class * extends androidx.work.InputMerger {
    public <init>();
}
