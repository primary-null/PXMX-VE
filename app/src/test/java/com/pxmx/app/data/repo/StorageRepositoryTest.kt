package com.pxmx.app.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageRepositoryTest {

    @Test
    fun sanitizeIsoFilename_acceptsValidFilename() {
        assertEquals("alpine-virt.iso", StorageRepository.sanitizeIsoFilename("alpine-virt.iso"))
        assertEquals("alpine-virt.iso", StorageRepository.sanitizeIsoFilename("  alpine-virt.iso  "))
        assertEquals("debian-12.0.0-amd64-netinst.ISO", StorageRepository.sanitizeIsoFilename("debian-12.0.0-amd64-netinst.ISO"))
        assertEquals("Rocky-9.3-x86_64-minimal.iso", StorageRepository.sanitizeIsoFilename("Rocky-9.3-x86_64-minimal.iso"))
    }

    @Test
    fun sanitizeIsoFilename_rejectsTraversalSequences() {
        val ex1 = assertThrows(PveException::class.java) {
            StorageRepository.sanitizeIsoFilename("../x.iso")
        }
        assertTrue(ex1.message?.contains("contains path separators or traversal sequences") == true)

        val ex2 = assertThrows(PveException::class.java) {
            StorageRepository.sanitizeIsoFilename("a/b.iso")
        }
        assertTrue(ex2.message?.contains("contains path separators or traversal sequences") == true)

        val ex3 = assertThrows(PveException::class.java) {
            StorageRepository.sanitizeIsoFilename("a\\b.iso")
        }
        assertTrue(ex3.message?.contains("contains path separators or traversal sequences") == true)
    }

    @Test
    fun sanitizeIsoFilename_rejectsMissingIsoExtension() {
        val ex = assertThrows(PveException::class.java) {
            StorageRepository.sanitizeIsoFilename("nope.txt")
        }
        assertTrue(ex.message?.contains("missing required .iso extension") == true)
    }

    @Test
    fun sanitizeIsoFilename_rejectsEmptyBaseFilename() {
        val ex = assertThrows(PveException::class.java) {
            StorageRepository.sanitizeIsoFilename(".iso")
        }
        assertTrue(ex.message?.contains("filename base is empty") == true)
    }

    @Test
    fun sanitizeIsoFilename_rejectsDisallowedCharacters() {
        val ex = assertThrows(PveException::class.java) {
            StorageRepository.sanitizeIsoFilename("test file;rm.iso")
        }
        assertTrue(ex.message?.contains("contains disallowed characters") == true)
    }
}
