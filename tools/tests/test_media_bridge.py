"""Offline regression tests for the on-device yt-dlp adapter (synthetic metadata)."""
import json
import sys
import unittest
from unittest.mock import patch
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[2] / 'app/src/main/python'))
from liuce_media import LiuceXhsIE, LiuceDouyinIE, flatten_info, safe_headers, QuietLogger, xhs_album, resolve
from yt_dlp import YoutubeDL
from yt_dlp.utils import ExtractorError

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

    def test_xhs_alternate_image_data_prefers_full_items_not_preview(self):
        note = {'type': 'normal', 'image_list': [
            {'info_list': [{'imageScene': 'WB_DFT', 'url': 'https://example.com/original-1.jpg'},
                           {'imageScene': 'WB_PRV', 'url': 'https://example.com/preview-1.jpg'}]},
            {'url_default': 'https://example.com/original-2.webp'}]}
        album = xhs_album(note, 'synthetic-note')
        self.assertEqual([item['url'] for item in album['media']],
                         ['https://example.com/original-1.jpg', 'https://example.com/original-2.webp'])
        self.assertIsNone(xhs_album({'type':'video', 'video':{}, 'imageList':[]}, 'synthetic-note'))

    def test_xhs_short_cn_share_resolves_to_long_item_and_keeps_all_images(self):
        target = 'https://www.xiaohongshu.com/discovery/item/6411cf99000000001300b6d9?xsec_token=synthetic'
        class Redirect:
            url = target
            def __enter__(self): return self
            def __exit__(self, *_): return False
        album = {'title':'完整图文', '_type':'liuce_album', 'media':[
            {'url':'https://example.com/image1.jpg','kind':'image','ext':'jpg'},
            {'url':'https://example.com/image2.jpg','kind':'image','ext':'jpg'}]}
        with patch.object(YoutubeDL, 'urlopen', return_value=Redirect()), patch.object(LiuceXhsIE, 'extract', return_value=album) as extractor:
            result = json.loads(resolve('https://xhslink.cn/o/SyntheticShort', 'web_session=fake'))
        extractor.assert_called_once_with(target)
        self.assertEqual(result['source'], target)
        self.assertEqual(len(result['media']), 2)
        self.assertTrue(all(item['headers']['Referer']==target for item in result['media']))

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

    def test_other_social_video_playlists_keep_real_media_without_thumbnails(self):
        info = {'_type':'playlist', 'entries':[
            {'title':'微博视频','formats':[{'url':'https://cdn.example.com/weibo.mp4','ext':'mp4','acodec':'aac','vcodec':'h264'}],
             'thumbnail':'https://cdn.example.com/weibo-cover.jpg'},
            {'title':'快手视频','formats':[{'url':'https://cdn.example.com/kuaishou.mp4','ext':'mp4','acodec':'aac','vcodec':'h264'}]}]}
        items = flatten_info(info)
        self.assertEqual([item['url'] for item in items],
                         ['https://cdn.example.com/weibo.mp4','https://cdn.example.com/kuaishou.mp4'])
        self.assertTrue(all(item['kind']=='video' for item in items))

    def test_stream_only_or_thumbnail_only_page_cannot_pretend_to_be_downloaded(self):
        for info in (
            {'url':'https://example.com/post/123', 'title':'只有网页'},
            {'thumbnail':'https://example.com/cover.jpg','formats':[]},
            {'formats':[{'url':'https://example.com/stream.m3u8','ext':'mp4','protocol':'m3u8_native'}]},
            {'formats':[{'url':'https://example.com/chunk.m4s','ext':'mp4','protocol':'http_dash_segments'}]}):
            with self.subTest(info=info):
                with self.assertRaises(ExtractorError): flatten_info(info)

if __name__ == '__main__':
    unittest.main()
