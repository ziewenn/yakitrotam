package com.yakitrotam.app

import com.yakitrotam.app.data.model.LatLng
import com.yakitrotam.app.domain.RefSegment
import com.yakitrotam.app.domain.Tolls
import org.junit.Assert.*
import org.junit.Test

class TollsTest {
    @Test
    fun `uzun kopru iki uc noktasiyla da sayilir`() {
        // 1915 Köprüsü ortası (40.33897, 26.63798); rota yalnızca iki ucundan geçiyor.
        val route = listOf(LatLng(40.36, 26.62), LatLng(40.318, 26.656))
        val estimate = Tolls.estimate(route, emptyList())!!
        assertEquals(listOf("1915 Çanakkale Köprüsü"), estimate.items.map { it.name })
        assertEquals(1170.0, estimate.totalTL, 0.0)
    }

    @Test
    fun `otoyol km oranina gore ve uc uca ucreti asmaz`() {
        val bursa = LatLng(40.2, 29.0)
        val half = Tolls.estimate(emptyList(), listOf(RefSegment("O-5", bursa, 200.0)))!!
        assertEquals(863.0, half.totalTL, 0.0)
        val full = Tolls.estimate(emptyList(), listOf(RefSegment("O-5", bursa, 300.0), RefSegment("O-5", bursa, 120.0)))!!
        assertEquals(1725.0, full.totalTL, 0.0)
        // Rampa parçaları ve ücretsiz çevre yolları sayılmaz.
        assertNull(Tolls.estimate(emptyList(), listOf(RefSegment("O-5", bursa, 1.0), RefSegment("O-20", bursa, 30.0))))
        // "O-30; E881" gibi çoklu numaralar ayrılır; O-21 enleme göre iki tarifeye bölünür.
        val ankara = Tolls.estimate(emptyList(), listOf(RefSegment("O-21; E90", LatLng(39.5, 32.9), 275.0)))!!
        assertEquals(925.0, ankara.totalTL, 0.0)
    }
}
