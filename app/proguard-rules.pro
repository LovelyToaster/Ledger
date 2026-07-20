# Apache POI
-keep class org.apache.poi.** { *; }
-dontwarn org.apache.poi.**
-dontwarn org.openxmlformats.** 
-dontwarn com.microsoft.schemas.**
-dontwarn aQute.bnd.**
-dontwarn org.osgi.framework.**
-keep class org.apache.logging.log4j.** { *; }
-dontwarn org.apache.logging.log4j.**

# Amap Location SDK
-keep class com.amap.api.location.**{*;}
-keep class com.amap.api.fence.**{*;}
-keep class com.loc.**{*;}
-dontwarn com.amap.ams.gnss.**
-dontwarn net.jafama.**

# Incremental sync serializable classes
-keepclassmembers class com.verdantgem.ledger.data.remote.SyncManifest { <fields>; }
-keepclassmembers class com.verdantgem.ledger.data.remote.DeviceState { <fields>; }
-keepclassmembers class com.verdantgem.ledger.data.remote.SyncChangeBatch { <fields>; }
-keepclassmembers class com.verdantgem.ledger.data.local.SyncChangeLog { <fields>; }