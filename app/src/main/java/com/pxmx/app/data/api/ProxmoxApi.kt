package com.pxmx.app.data.api

import com.pxmx.app.data.model.ClusterLogEntry
import com.pxmx.app.data.model.ClusterResource
import com.pxmx.app.data.model.ConsoleProxyData
import com.pxmx.app.data.model.GuestStatus
import com.pxmx.app.data.model.HostUsbDevice
import com.pxmx.app.data.model.NodeStatus
import com.pxmx.app.data.model.NodeStorageEntry
import com.pxmx.app.data.model.PveResponse
import com.pxmx.app.data.model.SnapshotInfo
import com.pxmx.app.data.model.StorageContentItem
import com.pxmx.app.data.model.StorageStatus
import com.pxmx.app.data.model.TaskStatus
import com.pxmx.app.data.model.TicketData
import com.pxmx.app.data.model.VersionInfo
import kotlinx.serialization.Contextual
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.Field
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

interface ProxmoxApi {

    @FormUrlEncoded
    @POST("access/ticket")
    suspend fun createTicket(
        @Field("username") username: String,
        @Field("password") password: String,
    ): PveResponse<TicketData>

    @FormUrlEncoded
    @POST("access/tfa")
    suspend fun accessTfa(
        @Field("password") password: String,
        @Field("otp") otp: String,
    ): PveResponse<TicketData>

    @GET("version")
    suspend fun version(): PveResponse<VersionInfo>

    @GET("cluster/resources")
    suspend fun clusterResources(
        @Query("type") type: String? = null,
    ): PveResponse<List<ClusterResource>>

    @GET("cluster/status")
    suspend fun clusterStatus(): PveResponse<List<Map<String, @Contextual Any>>>

    @GET("cluster/log")
    suspend fun clusterLog(
        @Query("max") max: Int,
        @Query("since") since: Long? = null,
    ): PveResponse<List<ClusterLogEntry>>

    @GET("nodes")
    suspend fun nodes(): PveResponse<List<ClusterResource>>

    @GET("nodes/{node}/status")
    suspend fun nodeStatus(
        @Path("node") node: String,
    ): PveResponse<NodeStatus>

    @GET("nodes/{node}/qemu")
    suspend fun nodeQemu(
        @Path("node") node: String,
    ): PveResponse<List<ClusterResource>>

    @GET("nodes/{node}/lxc")
    suspend fun nodeLxc(
        @Path("node") node: String,
    ): PveResponse<List<ClusterResource>>

    @GET("nodes/{node}/storage")
    suspend fun nodeStorage(
        @Path("node") node: String,
    ): PveResponse<List<NodeStorageEntry>>

    @GET("nodes/{node}/{type}/{vmid}/status/current")
    suspend fun guestStatus(
        @Path("node") node: String,
        @Path("type") type: String,
        @Path("vmid") vmid: Long,
    ): PveResponse<GuestStatus>

    @FormUrlEncoded
    @POST("nodes/{node}/{type}/{vmid}/clone")
    suspend fun cloneGuest(
        @Path("node") node: String,
        @Path("type") type: String,
        @Path("vmid") vmid: Long,
        @Field("newid") newid: Long,
        @Field("name") name: String? = null,
        @Field("hostname") hostname: String? = null,
    ): PveResponse<String>

    @POST("nodes/{node}/{type}/{vmid}/status/{action}")
    suspend fun guestAction(
        @Path("node") node: String,
        @Path("type") type: String,
        @Path("vmid") vmid: Long,
        @Path("action") action: String,
    ): PveResponse<String>

    @GET("nodes/{node}/{type}/{vmid}/config")
    suspend fun guestConfig(
        @Path("node") node: String,
        @Path("type") type: String,
        @Path("vmid") vmid: Long,
        @Query("current") current: Int? = 1,
    ): PveResponse<Map<String, @Contextual Any>>

    @GET("nodes/{node}/{type}/{vmid}/snapshot")
    suspend fun guestSnapshots(
        @Path("node") node: String,
        @Path("type") type: String,
        @Path("vmid") vmid: Long,
    ): PveResponse<List<SnapshotInfo>>

    @FormUrlEncoded
    @POST("nodes/{node}/{type}/{vmid}/snapshot")
    suspend fun createSnapshot(
        @Path("node") node: String,
        @Path("type") type: String,
        @Path("vmid") vmid: Long,
        @Field("snapname") snapname: String,
        @Field("description") description: String? = null,
        @Field("vmstate") vmstate: Int? = null,
    ): PveResponse<String>

    @DELETE("nodes/{node}/{type}/{vmid}/snapshot/{snap}")
    suspend fun deleteSnapshot(
        @Path("node") node: String,
        @Path("type") type: String,
        @Path("vmid") vmid: Long,
        @Path("snap") snap: String,
        @Query("force") force: Int? = null,
    ): PveResponse<String>

    @POST("nodes/{node}/{type}/{vmid}/snapshot/{snap}/rollback")
    suspend fun rollbackSnapshot(
        @Path("node") node: String,
        @Path("type") type: String,
        @Path("vmid") vmid: Long,
        @Path("snap") snap: String,
    ): PveResponse<String>

    @GET("nodes/{node}/storage/{storage}/content")
    suspend fun storageContent(
        @Path("node") node: String,
        @Path("storage") storage: String,
        @Query("content") content: String? = null,
        @Query("vmid") vmid: Long? = null,
    ): PveResponse<List<StorageContentItem>>

    @GET("nodes/{node}/storage/{storage}/content/{volume}")
    suspend fun storageVolume(
        @Path("node") node: String,
        @Path("storage") storage: String,
        @Path("volume", encoded = true) volume: String,
    ): PveResponse<StorageContentItem>

    @GET("nodes/{node}/storage/{storage}/status")
    suspend fun storageStatus(
        @Path("node") node: String,
        @Path("storage") storage: String,
    ): PveResponse<StorageStatus>

    @FormUrlEncoded
    @POST("nodes/{node}/vzdump")
    suspend fun createBackup(
        @Path("node") node: String,
        @Field("vmid") vmid: Long,
        @Field("storage") storage: String,
        @Field("mode") mode: String? = "snapshot",
        @Field("compress") compress: String? = "zstd",
        @Field("remove") remove: Int? = 0,
        @Field("notes-template") notesTemplate: String? = null,
    ): PveResponse<String>

    @DELETE("nodes/{node}/storage/{storage}/content/{volume}")
    suspend fun deleteStorageContent(
        @Path("node") node: String,
        @Path("storage") storage: String,
        @Path("volume", encoded = true) volume: String,
    ): PveResponse<String>

    @GET("nodes/{node}/hardware/usb")
    suspend fun nodeUsb(
        @Path("node") node: String,
    ): PveResponse<List<HostUsbDevice>>

    /**
     * Update guest config (add usbN=host=…, or delete=usb0, …).
     * Returns null or a UPID string when a background task is started.
     */
    @FormUrlEncoded
    @PUT("nodes/{node}/{type}/{vmid}/config")
    suspend fun updateGuestConfig(
        @Path("node") node: String,
        @Path("type") type: String,
        @Path("vmid") vmid: Long,
        @FieldMap fields: Map<String, String>,
    ): PveResponse<String?>

    /** QEMU graphical console proxy (noVNC). */
    @FormUrlEncoded
    @POST("nodes/{node}/qemu/{vmid}/vncproxy")
    suspend fun qemuVncProxy(
        @Path("node") node: String,
        @Path("vmid") vmid: Long,
        @Field("websocket") websocket: Int = 1,
    ): PveResponse<ConsoleProxyData>

    /**
     * Legacy LXC serial terminal proxy (xterm.js).
     * Note: In PVE 8.3+, POST termproxy returns {"data":null} and rejects websocket=1 with 400.
     */
    @FormUrlEncoded
    @POST("nodes/{node}/lxc/{vmid}/termproxy")
    suspend fun lxcTermProxy(
        @Path("node") node: String,
        @Path("vmid") vmid: Long,
        @Field("websocket") websocket: Int = 1,
    ): PveResponse<ConsoleProxyData>

    @FormUrlEncoded
    @POST("nodes/{node}/termproxy")
    suspend fun nodeTermProxy(
        @Path("node") node: String,
        @Field("cmd") cmd: String? = null,
        @Field("cmd-opts") cmdOpts: String? = null,
    ): PveResponse<ConsoleProxyData>

    /** LXC console proxy (noVNC). Primary endpoint on PVE 8.3+. */
    @FormUrlEncoded
    @POST("nodes/{node}/lxc/{vmid}/vncproxy")
    suspend fun lxcVncProxy(
        @Path("node") node: String,
        @Path("vmid") vmid: Long,
        @Field("websocket") websocket: Int = 1,
    ): PveResponse<ConsoleProxyData>

    @GET("nodes/{node}/tasks/{upid}/status")
    suspend fun taskStatus(
        @Path("node") node: String,
        @Path("upid", encoded = true) upid: String,
    ): PveResponse<TaskStatus>

    @GET("cluster/tasks")
    suspend fun clusterTasks(): PveResponse<List<Map<String, @Contextual Any>>>

    /** Node network interfaces (read). */
    @GET("nodes/{node}/network")
    suspend fun nodeNetwork(
        @Path("node") node: String,
    ): PveResponse<List<Map<String, @Contextual Any>>>

    /** Available apt package updates for a node. */
    @GET("nodes/{node}/apt/update")
    suspend fun aptUpdateList(
        @Path("node") node: String,
    ): PveResponse<List<Map<String, @Contextual Any>>>

    /** Installed package versions (pve-manager, etc.). */
    @GET("nodes/{node}/apt/versions")
    suspend fun aptVersions(
        @Path("node") node: String,
    ): PveResponse<List<Map<String, @Contextual Any>>>

    /** Refresh apt update database (returns UPID task). */
    @POST("nodes/{node}/apt/update")
    suspend fun aptUpdateRefresh(
        @Path("node") node: String,
    ): PveResponse<String>

    /** Upgrade apt packages on a node (runs apt-get dist-upgrade, returns UPID task). */
    @POST("nodes/{node}/apt/upgrade")
    suspend fun aptUpgrade(
        @Path("node") node: String,
    ): PveResponse<String>

    /** Read task log lines for an active or finished task. */
    @GET("nodes/{node}/tasks/{upid}/log")
    suspend fun taskLog(
        @Path("node") node: String,
        @Path("upid", encoded = true) upid: String,
        @Query("start") start: Int? = null,
        @Query("limit") limit: Int? = null,
    ): PveResponse<List<Map<String, @Contextual Any>>>

    /** SDN zones (may be empty / 501 if SDN unused). */
    @GET("cluster/sdn/zones")
    suspend fun sdnZones(): PveResponse<List<Map<String, @Contextual Any>>>

    @GET("cluster/sdn/vnets")
    suspend fun sdnVnets(): PveResponse<List<Map<String, @Contextual Any>>>

    @GET("cluster/sdn/status")
    suspend fun sdnStatus(): PveResponse<List<Map<String, @Contextual Any>>>

    @GET("nodes/{node}/services")
    suspend fun nodeServices(
        @Path("node") node: String,
    ): PveResponse<List<Map<String, @Contextual Any>>>

    @GET("nodes/{node}/tasks")
    suspend fun nodeTasks(
        @Path("node") node: String,
        @Query("start") start: Int? = 0,
        @Query("limit") limit: Int? = 30,
    ): PveResponse<List<Map<String, @Contextual Any>>>

    @GET("cluster/firewall/options")
    suspend fun clusterFirewallOptions(): PveResponse<Map<String, @Contextual Any>>

    @GET("cluster/firewall/rules")
    suspend fun clusterFirewallRules(): PveResponse<List<Map<String, @Contextual Any>>>

    @GET("cluster/firewall/aliases")
    suspend fun clusterFirewallAliases(): PveResponse<List<Map<String, @Contextual Any>>>

    @GET("nodes/{node}/firewall/options")
    suspend fun nodeFirewallOptions(
        @Path("node") node: String,
    ): PveResponse<Map<String, @Contextual Any>>

    @GET("nodes/{node}/firewall/rules")
    suspend fun nodeFirewallRules(
        @Path("node") node: String,
    ): PveResponse<List<Map<String, @Contextual Any>>>
}
