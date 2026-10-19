"""Offline regression tests for the on-device yt-dlp adapter (synthetic metadata)."""
import json
import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[2] / 'app/src/main/python'))
from liuce_media import LiuceXhsIE, LiuceDouyinIE, flatten_info, safe_headers, QuietLogger
from yt_dlp import YoutubeDL

class MediaBridgeTest(unittest.TestCase):
    def engine(self):
        return YoutubeDL({'quiet': True, 'logger': QuietLogger(), 'cachedir': False})

    def test_xhs_album_preserves_image_order_without_previews(self):
        note_id = '6411cf99000000001300b6d9'
        note = {'type':'normal', 'title':'合成图集', 'imageList':[
            {'urlDefault':'https://example.com/full-1.jpg','urlPre':'https://example.com/thumbnail-1.jpg'},
            {'urlDefault':'https://example.com/full-2.jpg'}]}
        state = {'note':{'noteDetailMap':{note_id:{'note':note}}}}
        extractor = LiuceXhsIE(self.engine())
        extractor._download_webpage = lambda *_: '<script>window.__INITIAL_STATE__=' + json.dumps(state) + '</script>'
        result = extractor._real_extract('https://www.xiaohongshu.com/explore/' + note_id)
        self.assertEqual([m['url'] for m in flatten_info(result)], ['https://example.com/full-1.jpg','https://example.com/full-2.jpg'])
        self.assertTrue(all(m['kind']=='image' for m in flatten_info(result)))

    def test_douyin_album_uses_images_not_video_cover(self):
        extractor = LiuceDouyinIE(self.engine())
        extractor._download_json = lambda *_, **__: {'aweme_detail':{'desc':'合成作品','images':[
            {'url_list':['https://example.com/image-1.webp']},
            {'display_image':{'url_list':['https://example.com/image-2.webp']}}],
            'video':{'cover':{'url_list':['https://example.com/cover.jpg']}}}}
        result = extractor._real_extract('https://www.douyin.com/note/123456789')
        self.assertEqual(len(flatten_info(result)), 2)
        self.assertNotIn('cover.jpg', json.dumps(result))

    def test_raw_upstream_video_formats_keep_sound(self):
        result={'formats':[
            {'url':'https://example.com/720.mp4','ext':'mp4','height':720,'acodec':'aac','vcodec':'h264'},
            {'url':'https://example.com/silent.mp4','ext':'mp4','height':2160,'acodec':'none','vcodec':'h264'},
            {'url':'https://example.com/1080.mp4','ext':'mp4','height':1080,'acodec':'aac','vcodec':'h264'}]}
        self.assertEqual(flatten_info(result)[0]['url'], 'https://example.com/1080.mp4')
        self.assertEqual(safe_headers({'Cookie':'private','Authorization':'private','Referer':'https://example.com'}), {'Referer':'https://example.com'})

if __name__ == '__main__':
    unittest.main()
