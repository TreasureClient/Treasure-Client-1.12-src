//! Reading and writing 1.12.2 (protocol 340) packet fields.
#![allow(dead_code)] // some readers are for later stages
//!
//! 1.12 packets are big-endian with VarInts, the same building blocks as today, but
//! with older field layouts: packed block positions are `x:26 | y:12 | z:26`, chat is
//! JSON text, and keep-alive ids are longs.

use crate::util::wire::write_varint;

pub(super) struct In<'a> {
    buf: &'a [u8],
    at: usize,
}

impl<'a> In<'a> {
    pub(super) fn new(buf: &'a [u8]) -> Self {
        Self { buf, at: 0 }
    }

    /// Where the reader is, for `since`.
    pub(super) fn at(&self) -> usize {
        self.at
    }

    /// The bytes read since `start`.
    pub(super) fn since(&self, start: usize) -> &'a [u8] {
        &self.buf[start.min(self.at)..self.at]
    }

    pub(super) fn bytes(&mut self, n: usize) -> Option<&'a [u8]> {
        self.take(n)
    }

    fn take(&mut self, n: usize) -> Option<&'a [u8]> {
        let end = self.at.checked_add(n).filter(|end| *end <= self.buf.len())?;
        let slice = &self.buf[self.at..end];
        self.at = end;
        Some(slice)
    }

    fn array<const N: usize>(&mut self) -> Option<[u8; N]> {
        self.take(N)?.try_into().ok()
    }

    pub(super) fn u8(&mut self) -> Option<u8> {
        Some(self.take(1)?[0])
    }

    pub(super) fn i8(&mut self) -> Option<i8> {
        Some(self.u8()? as i8)
    }

    pub(super) fn bool(&mut self) -> Option<bool> {
        Some(self.u8()? != 0)
    }

    pub(super) fn i16(&mut self) -> Option<i16> {
        Some(i16::from_be_bytes(self.array()?))
    }

    pub(super) fn i32(&mut self) -> Option<i32> {
        Some(i32::from_be_bytes(self.array()?))
    }

    pub(super) fn i64(&mut self) -> Option<i64> {
        Some(i64::from_be_bytes(self.array()?))
    }

    pub(super) fn f32(&mut self) -> Option<f32> {
        Some(f32::from_be_bytes(self.array()?))
    }

    pub(super) fn f64(&mut self) -> Option<f64> {
        Some(f64::from_be_bytes(self.array()?))
    }

    pub(super) fn varint(&mut self) -> Option<i32> {
        let mut value: u32 = 0;
        for i in 0..5 {
            let byte = self.u8()?;
            value |= u32::from(byte & 0x7F) << (7 * i);
            if byte & 0x80 == 0 {
                return Some(value as i32);
            }
        }
        None
    }

    pub(super) fn string(&mut self) -> Option<String> {
        let len = usize::try_from(self.varint()?).ok()?;
        Some(String::from_utf8_lossy(self.take(len)?).into_owned())
    }

    /// A 1.12 packed block position: x (26 bits), y (12 bits), z (26 bits).
    pub(super) fn position(&mut self) -> Option<(i32, i32, i32)> {
        let v = self.i64()?;
        let x = (v >> 38) as i32;
        let y = ((v >> 26) & 0xFFF) as i32;
        let z = (v << 38 >> 38) as i32;
        Some((x, if y >= 0x800 { y - 0x1000 } else { y }, z))
    }

    pub(super) fn rest(&mut self) -> &'a [u8] {
        let rest = &self.buf[self.at..];
        self.at = self.buf.len();
        rest
    }
}

/// One 1.12 packet being written: the id first, then the fields.
pub(super) struct Out(Vec<u8>);

impl Out {
    pub(super) fn new(id: i32) -> Self {
        let mut out = Vec::with_capacity(32);
        write_varint(&mut out, id);
        Self(out)
    }

    pub(super) fn varint(mut self, v: i32) -> Self {
        write_varint(&mut self.0, v);
        self
    }

    pub(super) fn u8(mut self, v: u8) -> Self {
        self.0.push(v);
        self
    }

    pub(super) fn bool(self, v: bool) -> Self {
        self.u8(u8::from(v))
    }

    pub(super) fn i16(mut self, v: i16) -> Self {
        self.0.extend_from_slice(&v.to_be_bytes());
        self
    }

    pub(super) fn i64(mut self, v: i64) -> Self {
        self.0.extend_from_slice(&v.to_be_bytes());
        self
    }

    pub(super) fn f32(mut self, v: f32) -> Self {
        self.0.extend_from_slice(&v.to_be_bytes());
        self
    }

    pub(super) fn f64(mut self, v: f64) -> Self {
        self.0.extend_from_slice(&v.to_be_bytes());
        self
    }

    pub(super) fn string(mut self, v: &str) -> Self {
        write_varint(&mut self.0, v.len() as i32);
        self.0.extend_from_slice(v.as_bytes());
        self
    }

    /// A 1.12 packed block position.
    pub(super) fn position(self, x: i32, y: i32, z: i32) -> Self {
        let v = (i64::from(x) & 0x3FF_FFFF) << 38 | (i64::from(y) & 0xFFF) << 26 | (i64::from(z) & 0x3FF_FFFF);
        self.i64(v)
    }

    pub(super) fn bytes(mut self, v: &[u8]) -> Self {
        self.0.extend_from_slice(v);
        self
    }

    pub(super) fn done(self) -> Vec<u8> {
        self.0
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn packed_positions_round_trip_negative_coordinates() {
        let (x, y, z) = (-30_000_000 + 7, 255, 1_234_567);
        let v = ((x as i64 & 0x3FF_FFFF) << 38) | ((y as i64 & 0xFFF) << 26) | (z as i64 & 0x3FF_FFFF);
        let bytes = v.to_be_bytes();
        assert_eq!(In::new(&bytes).position(), Some((x, y, z)));
    }
}
