extends SceneTree
## Raw GLB and scripts packed for a runtime GLTFDocument import; no machine placeholders.

func _initialize() -> void:
	var scene_script := load("res://main.gd") as GDScript
	if scene_script == null or not scene_script.can_instantiate():
		printerr("Scene script did not compile; refusing to pack.")
		quit(1)
		return
	var args := OS.get_cmdline_user_args()
	if args.size() != 2 or not FileAccess.file_exists(args[1]):
		printerr("Usage: -- output.pck /absolute/test-fixture.glb")
		quit(2)
		return
	if not FileAccess.file_exists("res://.godot/global_script_class_cache.cfg"):
		printerr("Run Godot --headless --editor --path scene --quit before packing.")
		quit(2)
		return
	if ProjectSettings.save_custom("res://.godot/probe.binary") != OK:
		printerr("Could not save binary project settings.")
		quit(1)
		return
	if FileAccess.get_sha256(args[1]) != "a1e3b04de97b11de564ce6e53b95f02954a297f0008183ac63a4f5974f6b32d8":
		printerr("This probe is labelled for the verified DamagedHelmet fixture; other assets require their own attribution.")
		quit(2)
		return
	var packer := PCKPacker.new()
	var result := packer.pck_start(args[0])
	if result != OK:
		quit(1)
		return
	for name in ["project.godot", "main.gd", "main.tscn", "THIRD-PARTY.txt"]:
		result = packer.add_file("res://" + name, "res://" + name)
		if result != OK:
			printerr("Cannot pack " + name)
			quit(1)
			return
	result = packer.add_file("res://fixture.glb", args[1])
	if result == OK:
		result = packer.add_file("res://project.binary", "res://.godot/probe.binary")
	if result == OK:
		result = packer.add_file("res://.godot/global_script_class_cache.cfg", "res://.godot/global_script_class_cache.cfg")
	if result == OK:
		result = packer.flush()
	print("PROBE_PACK_READY" if result == OK else "PROBE_PACK_FAILED")
	quit(0 if result == OK else 1)
